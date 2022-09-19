/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.migration.client;

import io.swagger.models.apideclaration.Api;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.parser.ParseException;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.client.sp_migration.APIMStatMigrationException;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.apimgt.migration.dto.ResourceScopeInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.ResourceScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.dto.ScopeInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.APIURLMappingInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.APIInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.APIInfoScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.dto.APIScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.dto.AMAPIResourceScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifactImpl;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.api.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.FileUtil;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MigrateFrom310 extends MigrationClientBase implements MigrationClient {

    private static final Log log = LogFactory.getLog(ScopeRoleMappingPopulationClient.class);
    private static final String SEPERATOR = "/";
    private static final String SPLITTER = ":";
    private static final String TENANT_IDENTIFIER = "t";
    private static final String APPLICATION_ROLE_PREFIX = "Application/";
    private RegistryService registryService;

    public MigrateFrom310(String tenantArguments, String blackListTenantArguments, String tenantRange,
                          RegistryService registryService, TenantManager tenantManager)
            throws UserStoreException, APIManagementException {

        super(tenantArguments, blackListTenantArguments, tenantRange, tenantManager);
        this.registryService = registryService;
    }

    @Override
    public void databaseMigration() throws APIMigrationException, SQLException {

    }

    @Override
    public void registryResourceMigration() throws APIMigrationException {

        rxtMigration(registryService);
        updateEnableStoreInRxt();
    }

    @Override
    public void fileSystemMigration() throws APIMigrationException {

    }

    @Override
    public void cleanOldResources() throws APIMigrationException {

    }

    @Override
    public void statsMigration() throws APIMigrationException, APIMStatMigrationException {

    }

    @Override
    public void tierMigration(List<String> options) throws APIMigrationException {

    }

    @Override
    public void updateArtifacts() throws APIMigrationException {

    }

    @Override
    public void populateSPAPPs() throws APIMigrationException {

    }

    @Override
    public void populateScopeRoleMapping() throws APIMigrationException {

    }

    @Override
    public void updateScopeRoleMappings() throws APIMigrationException {

    }

    @Override
    public void scopeMigration() throws APIMigrationException {

        APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();
        // Step 1: remove duplicate entries for scopes attached to multiple resources of the same API
        ArrayList<APIScopeMappingDTO> duplicateList = new ArrayList<>();
        ArrayList<APIScopeMappingDTO> scopeAMData = apiMgtDAO.getAMScopeData();
        ArrayList<ResourceScopeInfoDTO> scopeResourceData = apiMgtDAO.getResourceScopeData();
        for (APIScopeMappingDTO scopeAMDataDTO : scopeAMData) {
            int flag = 0;
            for (ResourceScopeInfoDTO resourceScopeInfoDTO : scopeResourceData) {
                if (scopeAMDataDTO.getScopeId() == Integer.parseInt(resourceScopeInfoDTO.getScopeId())) {
                    flag += 1;
                }
            }
            if (flag == 0) {
                duplicateList.add(scopeAMDataDTO);
            }
        }
        apiMgtDAO.removeDuplicateScopeEntries(duplicateList);
        if (!duplicateList.isEmpty()) {
            log.info("WSO2 API-M Migration Task : Removed duplicate scope entries for scopes attached to multiple "
                    + "resources of the same API, from IDN_OAUTH2_SCOPE, AM_API_SCOPE and IDN_OAUTH2_SCOPE_BINDING tables");
        }

        // Step 2: Remove duplicate versioned scopes registered for versioned APIs
        ArrayList<APIInfoScopeMappingDTO> apiInfoScopeMappingDTOS = apiMgtDAO.getAPIInfoScopeData();
        Map<String, Integer> apiScopeToScopeIdMapping = new HashMap<>();
        boolean removedVersionedScopes = false;
        for (APIInfoScopeMappingDTO scopeInfoDTO : apiInfoScopeMappingDTOS) {
            String apiScopeKey = scopeInfoDTO.getApiName() + ":" + scopeInfoDTO.getApiProvider() +
                    ":" + scopeInfoDTO.getScopeName();
            if (apiScopeToScopeIdMapping.containsKey(apiScopeKey)) {
                int scopeId = apiScopeToScopeIdMapping.get(apiScopeKey);
                if (scopeId != scopeInfoDTO.getScopeId()) {
                    apiMgtDAO.updateScopeResource(scopeId, scopeInfoDTO.getResourcePath(), scopeInfoDTO.getScopeId());
                    APIScopeMappingDTO apiScopeMappingDTO = new APIScopeMappingDTO();
                    apiScopeMappingDTO.setApiId(scopeInfoDTO.getApiId());
                    apiScopeMappingDTO.setScopeId(scopeInfoDTO.getScopeId());
                    ArrayList<APIScopeMappingDTO> scopeRemovalList = new ArrayList<>();
                    scopeRemovalList.add(apiScopeMappingDTO);
                    apiMgtDAO.removeDuplicateScopeEntries(scopeRemovalList);
                    removedVersionedScopes = true;
                }
            } else {
                apiScopeToScopeIdMapping.put(apiScopeKey, scopeInfoDTO.getScopeId());
            }
        }
        if (removedVersionedScopes) {
            log.info("WSO2 API-M Migration Task : Removed duplicate scope entries for scopes attached to "
                    + "versioned APIs, from IDN_OAUTH2_SCOPE, AM_API_SCOPE and IDN_OAUTH2_SCOPE_BINDING tables");
        }

        // Step 3: Move entries in IDN_RESOURCE_SCOPE_MAPPING table to AM_API_RESOURCE_SCOPE_MAPPING table
        ArrayList<APIInfoDTO> apiData = apiMgtDAO.getAPIData();
        ArrayList<APIURLMappingInfoDTO> urlMappingData = apiMgtDAO.getAPIURLMappingData();
        List<AMAPIResourceScopeMappingDTO> amapiResourceScopeMappingDTOList = new ArrayList<>();
        for (APIInfoDTO apiInfoDTO : apiData) {
            String context = apiInfoDTO.getApiContext();
            String version = apiInfoDTO.getApiVersion();
            for (APIURLMappingInfoDTO apiurlMappingInfoDTO : urlMappingData) {
                if (apiurlMappingInfoDTO.getApiId() == apiInfoDTO.getApiId()) {
                    String resourcePath = context + "/" + version + apiurlMappingInfoDTO.getUrlPattern() + ":" +
                            apiurlMappingInfoDTO.getHttpMethod();
                    int urlMappingId = apiurlMappingInfoDTO.getUrlMappingId();
                    int scopeId = apiMgtDAO.getScopeId(resourcePath);
                    if (scopeId != -1) {
                        ScopeInfoDTO scopeInfoDTO = apiMgtDAO.getScopeInfoByScopeId(scopeId);
                        String scopeName = scopeInfoDTO.getScopeName();
                        int tenantId = scopeInfoDTO.getTenantID();
                        AMAPIResourceScopeMappingDTO amapiResourceScopeMappingDTO = new AMAPIResourceScopeMappingDTO();
                        amapiResourceScopeMappingDTO.setScopeName(scopeName);
                        amapiResourceScopeMappingDTO.setUrlMappingId(urlMappingId);
                        amapiResourceScopeMappingDTO.setTenantId(tenantId);
                        amapiResourceScopeMappingDTOList.add(amapiResourceScopeMappingDTO);
                    }
                }
            }
        }
        apiMgtDAO.addDataToResourceScopeMapping(amapiResourceScopeMappingDTOList);
        if (!amapiResourceScopeMappingDTOList.isEmpty()) {
            log.info("WSO2 API-M Migration Task : Moved entries in the IDN_RESOURCE_SCOPE_MAPPING table to "
                    + "AM_API_RESOURCE_SCOPE_MAPPING table");
        }
    }

    @Override
    public void spMigration() throws APIMigrationException {

        List<Tenant> tenantList = getTenantsArray();
        // Iterate for each tenant. The reason we do this migration step wise for each tenant is so that, we do not
        // overwhelm the amount of rows returned for each database call in systems with a large tenant count.
        for (Tenant tenant : tenantList) {
            ArrayList<String> consumerKeys = APIMgtDAO.getAppsOfTypeJWT(tenant.getId());
            if (consumerKeys != null) {
                log.info("WSO2 API-M Migration Task : Updating tokenType property of service providers for JWT "
                        + "typed applications in tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
                for (String consumerKey : consumerKeys) {
                    APIMgtDAO.updateTokenTypeToJWT(consumerKey);
                }
                log.info("WSO2 API-M Migration Task : Updated tokenType property of service providers identified "
                        + "by consumer keys " + String.join(",", consumerKeys) + " as JWT");
            }
        }
    }

    private void updateEnableStoreInRxt() {

        for (Tenant tenant : getTenantsArray()) {
            try {
                registryService.startTenantFlow(tenant);
                log.info("WSO2 API-M Migration Task : Updating APIs for tenant " + tenant.getId() + '(' +
                        tenant.getDomain() + ')');
                GenericArtifact[] artifacts = registryService.getGenericAPIArtifacts();
                for (GenericArtifact artifact : artifacts) {
                    String path = artifact.getPath();
                    if (registryService.isGovernanceRegistryResourceExists(path)) {
                        Object apiResource = registryService.getGovernanceRegistryResource(path);
                        if (apiResource == null) {
                            continue;
                        }
                        registryService.updateEnableStoreInRxt(path, artifact);
                    }
                }
                log.info("WSO2 API-M Migration Task : Completed Updating API artifacts tenant ---- " + tenant.getId()
                        + '(' + tenant.getDomain() + ')');
            } catch (GovernanceException e) {
                log.error("WSO2 API-M Migration Task : Error while accessing API artifact in registry for tenant "
                        + tenant.getId() + '(' + tenant.getDomain() + ')', e);
            } catch (RegistryException | UserStoreException e) {
                log.error("WSO2 API-M Migration Task : Error while updating API artifact in the registry for tenant "
                        + tenant.getId() + '(' + tenant.getDomain() + ')', e);
            } finally {
                registryService.endTenantFlow();
            }
        }
    }

    @Override
    public void updateAPIPropertyVisibility() {
        for (Tenant tenant : getTenantsArray()) {
            try {
                registryService.startTenantFlow(tenant);
                log.info("WSO2 API-M Migration Task : Updating API properties for tenant " + tenant.getId() +
                        '(' + tenant.getDomain() + ')');
                GenericArtifact[] artifacts = registryService.getGenericAPIArtifacts();
                for (GenericArtifact artifact : artifacts) {
                    String path = artifact.getPath();
                    if (registryService.isGovernanceRegistryResourceExists(path)) {
                        Object apiResource = registryService.getGovernanceRegistryResource(path);
                        if (apiResource == null) {
                            continue;
                        }
                        registryService.updateAPIPropertyVisibility(path);
                    }
                }
                log.info("WSO2 API-M Migration Task : Completed Updating API properties for tenant " + tenant.getId()
                        + '(' + tenant.getDomain() + ')');
            } catch (GovernanceException e) {
                log.error("WSO2 API-M Migration Task : Error while accessing API artifact in registry for tenant "
                        + tenant.getId() + '(' + tenant.getDomain() + ')', e);
            } catch (RegistryException | UserStoreException e) {
                log.error("WSO2 API-M Migration Task : Error while updating API artifact in the registry for tenant "
                        + tenant.getId() + '(' + tenant.getDomain() + ')', e);
            } finally {
                registryService.endTenantFlow();
            }
        }
    }

    /**
     * Update the API_TYPE in the database
     *
     * @throws APIMigrationException APIMigrationException
     */
    public void updateAPITypeInDB() throws APIMigrationException {
        TenantManager tenantManager = ServiceHolder.getRealmService().getTenantManager();

        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
                try {
                    int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                    APIUtil.loadTenantRegistry(apiTenantId);
                    startTenantFlow(tenant.getDomain());
                    Registry registry =
                            ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    GenericArtifactManager tenantArtifactManager = APIUtil.getArtifactManager(registry,
                            APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        for (GenericArtifact artifact : tenantArtifacts) {
                            String artifactPath = ((GenericArtifactImpl) artifact).getArtifactPath();
                            if (artifactPath.contains("/apimgt/applicationdata/apis/")) {
                                continue;
                            }
                            APIInfoDTO apiInfoDTO = new APIInfoDTO();
                            apiInfoDTO.setApiProvider(
                                    APIUtil.replaceEmailDomainBack(artifact.getAttribute("overview_provider")));
                            apiInfoDTO.setApiName(artifact.getAttribute("overview_name"));
                            apiInfoDTO.setApiVersion(artifact.getAttribute("overview_version"));
                            apiInfoDTO.setType(artifact.getAttribute("overview_type"));
                            apiInfoDTOList.add(apiInfoDTO);
                        }
                        APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();
                        apiMgtDAO.updateAPIType(apiInfoDTOList, tenant.getId(), tenant.getDomain());
                    }
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (RegistryException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error while initializing the registry", e);
        } catch (UserStoreException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error while Retrieving API artifact from the"
                    + " registry", e);
        }
    }

    private static void startTenantFlow(String tenantDomain) {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
    }
}
