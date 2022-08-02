package org.wso2.carbon.apimgt.migration.validator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.validator.utils.Utility;
import org.wso2.carbon.apimgt.migration.validator.utils.Utils;
import org.wso2.carbon.apimgt.migration.validator.utils.UtilsFactory;
import org.wso2.carbon.apimgt.migration.validator.validators.Validator;
import org.wso2.carbon.apimgt.migration.validator.validators.ValidatorFactory;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifactImpl;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ValidationHandler {
    private static final Log log = LogFactory.getLog(ValidationHandler.class);
    private String preMigrationStep;
    private String tenantArguments;
    private String tenantRangeArgs;
    private final String migratedVersion = "4.0.0";
    private String blackListTenantArguments = System.getProperty(Constants.ARG_MIGRATE_BLACKLIST_TENANTS);
    private final String[] validatorList = {Constants.preValidationService.API_AVAILABILITY_VALIDATION,
            Constants.preValidationService.API_DEFINITION_VALIDATION};
    private final Validator validator;

    public ValidationHandler(String migrateFromVersion, String preMigrationStep,
                             String tenantArguments, String tenantRangeArgs) {
        this.preMigrationStep = preMigrationStep;
        this. tenantArguments = tenantArguments;
        this.tenantRangeArgs = tenantRangeArgs;
        UtilsFactory utilsFactory = new UtilsFactory();
        Utils utils = utilsFactory.getVersionUtils(migrateFromVersion);
        ValidatorFactory validatorFactory = new ValidatorFactory(utils);
        this.validator = validatorFactory.getVersionValidator(migratedVersion);
    }

    public void doValidation() throws UserStoreException, APIMigrationException {
        List<Tenant> tenants = loadTenants();
        if (Arrays.asList(validatorList).contains(preMigrationStep)) {
            for (Tenant tenant : tenants) {
                validateRegistryData(tenant, preMigrationStep);
            }
        } else {
            log.info("Running all validator steps.........");
            for (String validatorStep : validatorList) {
                for (Tenant tenant : tenants) {
                    validateRegistryData(tenant, validatorStep);
                }
            }
        }
    }

    private void validateRegistryData(Tenant tenant, String preMigrationStep) throws APIMigrationException {
        try {
            ServiceHolder.getTenantRegLoader().loadTenantRegistry(tenant.getId());
            UserRegistry registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(tenant.getId());
            GenericArtifactManager artifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);
            if (artifactManager != null) {
                GovernanceUtils.loadGovernanceArtifacts(registry);
                GenericArtifact[] artifacts = artifactManager.getAllGenericArtifacts();
                String artifactPath = "";
                log.info("Starting " + preMigrationStep + " for tenant " + tenant.getId() +
                        "(" + tenant.getDomain() + ")" + "..........");
                for (GenericArtifact artifact : artifacts) {
                    try {
                        artifactPath = ((GenericArtifactImpl) artifact).getArtifactPath();
                        if (log.isDebugEnabled()) {
                            log.debug("artifact path:  " + artifactPath);
                        }
                        if (artifactPath.contains("/apimgt/applicationdata/apis/")) {
                            continue;
                        }
                        validator.validate(registry, artifact, preMigrationStep);
                    } catch (Exception e) {
                        throw new APIMigrationException("Error occurred while retrieving API from the registry: "
                                + "artifact path name " + artifactPath, e);
                    }
                }
                log.info("Successfully completed " + preMigrationStep + " for tenant " + tenant.getId() +
                        "(" + tenant.getDomain() + ")" + "..........");
            } else {
                log.info("No API artifacts found in registry for tenant " + tenant.getId() + '(' + tenant.getDomain()
                        + ')');
            }
        } catch (APIManagementException e) {
            throw new APIMigrationException("Error occurred while reading API from the artifact ", e);
        } catch (RegistryException e) {
            throw new APIMigrationException("Error occurred while accessing the registry ", e);
        }
    }

    public List<Tenant> loadTenants() throws UserStoreException {
        TenantManager tenantManager = ServiceHolder.getRealmService().getTenantManager();
        List<Tenant> tenantsArray;
        String tenantRange = tenantRangeArgs;
        if (tenantArguments != null) {  // Tenant arguments have been provided so need to load specific ones
            tenantArguments = tenantArguments.replaceAll("\\s", ""); // Remove spaces and tabs
            tenantsArray = new ArrayList<>();
            Utility.buildTenantList(tenantManager, tenantsArray, tenantArguments);
            this.tenantArguments = tenantArguments;
        } else if (blackListTenantArguments != null) {
            blackListTenantArguments = blackListTenantArguments.replaceAll("\\s", ""); // Remove spaces and tabs
            List<Tenant> blackListTenants = new ArrayList<>();
            Utility.buildTenantList(tenantManager, blackListTenants, blackListTenantArguments);
            this.blackListTenantArguments = blackListTenantArguments;
            List<Tenant> allTenants = new ArrayList<>(Arrays.asList(tenantManager.getAllTenants()));
            Tenant superTenant = new Tenant();
            superTenant.setDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            superTenant.setId(MultitenantConstants.SUPER_TENANT_ID);
            allTenants.add(superTenant);
            tenantsArray = new ArrayList<>();
            for (Tenant tenant : allTenants) {
                boolean isBlackListed = false;
                for (Tenant blackListTenant : blackListTenants) {
                    if (blackListTenant.getId() == tenant.getId()) {
                        isBlackListed = true;
                        break;
                    }
                }
                if (!isBlackListed) {
                    tenantsArray.add(tenant);
                }
            }
        } else if (tenantRange != null) {
            tenantsArray = new ArrayList<>();
            int l, u;
            try {
                l = Integer.parseInt(tenantRange.split("-")[0].trim());
                u = Integer.parseInt(tenantRange.split("-")[1].trim());
            } catch (Exception e) {
                throw new UserStoreException("TenantRange argument is not properly set. use format 1-12", e);
            }
            log.debug("no of Tenants " + tenantManager.getAllTenants().length);
            int lastIndex = tenantManager.getAllTenants().length - 1;
            log.debug("last Tenant id " + tenantManager.getAllTenants()[lastIndex].getId());
            for (Tenant t : tenantManager.getAllTenants()) {
                if (t.getId() > l && t.getId() < u) {
                    log.debug("using tenants " + t.getDomain() + "(" + t.getId() + ")");
                    tenantsArray.add(t);
                }
            }
        } else {  // Load all tenants
            tenantsArray = new ArrayList<>(Arrays.asList(tenantManager.getAllTenants()));
            Tenant superTenant = new Tenant();
            superTenant.setDomain(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME);
            superTenant.setId(MultitenantConstants.SUPER_TENANT_ID);
            tenantsArray.add(superTenant);
        }
        log.debug("Setting tenant admin names");
        for (int i = 0; i < tenantsArray.size(); ++i) {
            Tenant tenant = tenantsArray.get(i);
            if (tenant.getId() == MultitenantConstants.SUPER_TENANT_ID) {
                tenant.setAdminName("admin");
            } else {
                tenantsArray.set(i, tenantManager.getTenant(tenant.getId()));
            }
        }
        return tenantsArray;
    }
}
