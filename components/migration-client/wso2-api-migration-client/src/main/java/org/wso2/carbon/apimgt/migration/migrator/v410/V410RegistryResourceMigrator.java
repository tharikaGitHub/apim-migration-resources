package org.wso2.carbon.apimgt.migration.migrator.v410;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIVersionComparator;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.RegistryResourceMigrator;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.apimgt.migration.util.APIUtil;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.apimgt.migration.util.RegistryServiceImpl;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifactImpl;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.wso2.carbon.utils.multitenancy.MultitenantUtils.getTenantAwareUsername;

public class V410RegistryResourceMigrator extends RegistryResourceMigrator {
    private static final Log log = LogFactory.getLog(V410RegistryResourceMigrator.class);
    private RegistryService registryService;
    APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();
    List<Tenant> tenants;

    public V410RegistryResourceMigrator(String rxtDir) throws UserStoreException {
        super(rxtDir);
        registryService = new RegistryServiceImpl();
        tenants = loadTenants();
    }

    public void migrate() throws APIMigrationException {
        super.migrate();
        registryDataPopulation();
    }

    /**
     * Updates versionComparable field of APIs in registry() and AM_DB
     * @throws APIMigrationException
     */
    private void registryDataPopulation() throws APIMigrationException {

        log.info("WSO2 API-M Migration Task : Starting registry data migration for API Manager " +
                Constants.VERSION_4_1_0);

        boolean isTenantFlowStarted = false;
        for (Tenant tenant : tenants) {
            log.info("WSO2 API-M Migration Task : Starting registry data migration for tenant " + tenant.getId()
                    + '(' + tenant.getDomain() + ')');

            try {
                PrivilegedCarbonContext.startTenantFlow();
                isTenantFlowStarted = true;

                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenant.getDomain(), true);
                PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenant.getId(), true);

                String adminName = getTenantAwareUsername(APIUtil.replaceEmailDomainBack(tenant.getAdminName()));
                log.info("WSO2 API-M Migration Task : Tenant admin username : " + adminName);

                ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());
                UserRegistry registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenant.getId());
                GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);

                if (artifactManager != null) {
                    GovernanceUtils.loadGovernanceArtifacts(registry);
                    GenericArtifact[] artifacts = artifactManager.getAllGenericArtifacts();
                    Map<String, List<API>> apisMap = new TreeMap<>();
                    Map<API, GenericArtifact> apiToArtifactMapping = new HashMap<>();

                    for (GenericArtifact artifact : artifacts) {
                        String apiIdentifier = artifact.getAttribute(Constants.API_OVERVIEW_PROVIDER)
                                + '-' + artifact.getAttribute(Constants.API_OVERVIEW_NAME) + '-'
                                + artifact.getAttribute(Constants.API_OVERVIEW_VERSION);
                        try {
                            String artifactPath = ((GenericArtifactImpl) artifact).getArtifactPath();
                            if (artifactPath.contains("/apimgt/applicationdata/apis/")) {
                                continue;
                            }
                            API api = org.wso2.carbon.apimgt.migration.util.APIUtil.getAPI(artifact, registry);
                            if (StringUtils.isNotEmpty(api.getVersionTimestamp())) {
                                log.info("WSO2 API-M Migration Task : VersionTimestamp already available in "
                                        + "APIName: " + api.getId().getApiName() + api.getId().getVersion());
                            }
                            if (api == null) {
                                log.error("WSO2 API-M Migration Task : Cannot find corresponding api for registry "
                                        + "artifact " + apiIdentifier + " of tenant "
                                        + tenant.getId() + '(' + tenant.getDomain() + ") in AM_DB");
                                continue;
                            }

                            if (!apisMap.containsKey(api.getId().getApiName())) {
                                List<API> versionedAPIsList = new ArrayList<>();
                                apisMap.put(api.getId().getApiName(), versionedAPIsList);
                            }
                            apisMap.get(api.getId().getApiName()).add(api);
                            if (!apiToArtifactMapping.containsKey(api)) {
                                apiToArtifactMapping.put(api, artifact);
                            }
                        } catch (Exception e) {
                            // we log the error and continue to the next resource.
                            throw new APIMigrationException("WSO2 API-M Migration Task : Unable to migrate api metadata"
                                    + " definition of API : " + apiIdentifier, e);
                        }
                    }

                    // set the versionTimestamp for each API
                    for (String apiName : apisMap.keySet()) {
                        List<API> versionedAPIList = apisMap.get(apiName);
                        versionedAPIList.sort(new APIVersionComparator());
                        long versionTimestamp = System.currentTimeMillis();
                        long oneDay = 86400;

                        log.info("WSO2 API-M Migration Task : Starting the registry data migration for versioned"
                                + " APIs by name: " + apiName + " of tenant " + tenant.getId() + '('
                                + tenant.getDomain() + ")");
                        log.info("WSO2 API-M Migration Task : Versioned APIs count: " + versionedAPIList.size());

                        for (int i = versionedAPIList.size(); i > 0; i--) {
                            API apiN = versionedAPIList.get(i - 1);
                            apiN.setVersionTimestamp(versionTimestamp + "");
                            apiToArtifactMapping.get(apiN).setAttribute(Constants.API_OVERVIEW_VERSION_COMPARABLE,
                                    String.valueOf(versionTimestamp));
                            log.info("WSO2 API-M Migration Task : Setting Version Comparable for API: "
                                    + apiN.getId() + ", UUID: " + apiN.getUuid());
                            try {
                                artifactManager.updateGenericArtifact(apiToArtifactMapping.get(apiN));
                            } catch (GovernanceException e) {
                                throw new APIMigrationException("WSO2 API-M Migration Task : Failed to update "
                                        + "versionComparable for API: " + apiN.getId().getApiName()
                                        + " version: " + apiN.getId().getVersion() + " versionComparable: "
                                        + apiN.getVersionTimestamp() + " at registry");
                            }
                            versionTimestamp -= oneDay;
                            GenericArtifact artifact;
                            try {
                                artifact = artifactManager.getGenericArtifact(apiN.getUuid());
                            } catch (GovernanceException e) {
                                throw new APIMigrationException("WSO2 API-M Migration Task : Failed to retrieve API: "
                                        + apiN.getId().getApiName() + " version: " + apiN.getId().getVersion()
                                        + " from registry.");
                            }
                            // validate registry update
                            API api = org.wso2.carbon.apimgt.migration.util.APIUtil.getAPI(artifact, registry);
                            if (StringUtils.isEmpty(api.getVersionTimestamp())) {
                                log.error("WSO2 API-M Migration Task : VersionComparable is empty for API: "
                                        + apiN.getId().getApiName() + " version: " + apiN.getId().getVersion()
                                        + " versionComparable: " + api.getVersionTimestamp() + " at registry.");
                            } else {
                                log.info("WSO2 API-M Migration Task : VersionTimestamp successfully updated for "
                                        + "API: " + apiN.getId().getApiName() + " version: " + apiN.getId().getVersion()
                                        + " versionComparable: " + api.getVersionTimestamp());
                            }
                        }
                        try {
                            apiMgtDAO.populateApiVersionTimestamp(versionedAPIList, tenant.getId(), tenant.getDomain());
                        } catch (APIMigrationException e) {
                            throw new APIMigrationException("WSO2 API-M Migration Task : Exception while populating"
                                    + " versionComparable for api " + apiName + " tenant: " + tenant.getDomain()
                                    + "at database");
                        }
                    }
                    log.info("WSO2 API-M Migration Task : Successfully migrated data for api rxts to include "
                            + "versionComparable of tenant:" + tenant.getId() + '(' + tenant.getDomain() + ')');
                } else {
                    log.info("WSO2 API-M Migration Task : No API artifacts found in registry for tenant "
                            + tenant.getId() + '(' + tenant.getDomain() + ')');

                }
            } catch (APIManagementException e) {
                throw new APIMigrationException("WSO2 API-M Migration Task : Error occurred while reading API from the"
                        + " artifact ", e);
            } catch (RegistryException e) {
                throw new APIMigrationException("WSO2 API-M Migration Task : Error occurred while accessing the "
                        + "registry ", e);
            } finally {
                if (isTenantFlowStarted) {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
            log.info("WSO2 API-M Migration Task : Completed registry data migration for tenant " + tenant.getId()
                    + '(' + tenant.getDomain() + ')');

        }
        log.info("WSO2 API-M Migration Task : API registry data migration done for all the tenants");
    }
}
