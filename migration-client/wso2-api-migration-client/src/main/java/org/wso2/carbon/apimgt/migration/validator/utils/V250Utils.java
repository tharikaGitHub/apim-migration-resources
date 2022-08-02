package org.wso2.carbon.apimgt.migration.validator.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.wsdl.util.SOAPOperationBindingUtils;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.registry.core.RegistryConstants;

public class V250Utils extends Utils {
    private static final Log log = LogFactory.getLog(V260Utils.class);
    public V250Utils(String migrateFromVersion) {
        super(migrateFromVersion);
    }

    @Override
    public String getWSDLArchivePath(String apiName, String apiVersion, String provider) {
        return APIConstants.API_WSDL_RESOURCE_LOCATION + APIConstants.API_WSDL_ARCHIVE_LOCATION + provider
                + APIConstants.WSDL_PROVIDER_SEPERATOR + apiName + apiVersion + APIConstants.ZIP_FILE_EXTENSION;
    }

    @Override
    public String getAPIPath(String apiName, String apiVersion, String provider) {
        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + provider
                + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR + apiVersion
                + APIConstants.API_RESOURCE_NAME;
    }

    @Override
    public String getWSDLPath(String apiName, String apiVersion, String provider) {
        return APIConstants.API_WSDL_RESOURCE_LOCATION + provider + APIConstants.WSDL_PROVIDER_SEPERATOR
                + apiName + apiVersion + APIConstants.WSDL_FILE_EXTENSION;
    }

    /**
     * Returns API Type
     *
     * @param artifact API Registry artifact
     * @return API Type
     * @throws GovernanceException
     */
    public static String getAPIType(GenericArtifact artifact) throws GovernanceException {
        String apiType = artifact.getAttribute(Constants.API_OVERVIEW_TYPE);
        String overview_wsdl = artifact.getAttribute(Constants.API_OVERVIEW_WSDL);
        if (!StringUtils.isEmpty(overview_wsdl)) {
            try {
                if (SOAPOperationBindingUtils.isSOAPToRESTApi(artifact.getAttribute(Constants.API_OVERVIEW_NAME),
                        artifact.getAttribute(Constants.API_OVERVIEW_VERSION),
                        artifact.getAttribute(Constants.API_OVERVIEW_PROVIDER))) {
                    apiType = Constants.API_TYPE_SOAPTOREST;
                } else {
                    apiType = Constants.API_TYPE_SOAP;
                }
            } catch (APIManagementException e) {
                log.error("Error occurred when getting attributes from artifact manager", e);
            }
        }
        return apiType;
    }
}
