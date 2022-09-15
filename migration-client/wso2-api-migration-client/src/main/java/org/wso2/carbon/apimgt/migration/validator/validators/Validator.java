package org.wso2.carbon.apimgt.migration.validator.validators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.validator.utils.Utils;
import org.wso2.carbon.apimgt.migration.validator.utils.V260Utils;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.registry.core.session.UserRegistry;

/**
 * Validator specific implementation
 */
public abstract class Validator {
    private static final Log log = LogFactory.getLog(Validator.class);
    protected Utils utils;
    protected UserRegistry registry;
    protected String apiName;
    protected String apiVersion;
    protected String provider;
    protected String apiType;
    protected String apiId;

    public Validator(Utils utils) {
        this.utils = utils;
    }

    public void validate(UserRegistry registry, GenericArtifact artifact, String preMigrationStep)
            throws GovernanceException {
        this.registry = registry;
        this.apiName = artifact.getAttribute(APIConstants.API_OVERVIEW_NAME);
        this.apiVersion = artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
        this.provider = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER);

        // At this point of  pre-validation step, SOAP and SOAPTOREST APIs from 2.6.0 will have their overview_type
        // set as HTTP, hence we are employing a Util method to fetch correct API Type based on other resources and
        // artifact fields.
        if (Constants.VERSION_2_6_0.equals(utils.getMigrateFromVersion())) {
            this.apiType = V260Utils.getAPIType(artifact);
        } else {
            this.apiType = artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE);
        }

        this.apiId = artifact.getId();
        if (Constants.preValidationService.API_ENDPOINT_VALIDATION.equals(preMigrationStep)) {
            validateEndpoints();
        } else if (Constants.preValidationService.API_DEFINITION_VALIDATION.equals(preMigrationStep)) {
            validateAPIDefinition();
        } else if (Constants.preValidationService.API_AVAILABILITY_VALIDATION.equals(preMigrationStep)) {
            validateApiAvailability();
        } else if (Constants.preValidationService.API_RESOURCE_LEVEL_AUTH_SCHEME_VALIDATION.equals(preMigrationStep)) {
            validateApiResourceLevelAuthScheme();
        }
    }

    public abstract void validateEndpoints();

    public abstract void validateAPIDefinition();

    public abstract void validateApiAvailability();

    public abstract void validateApiResourceLevelAuthScheme();
}
