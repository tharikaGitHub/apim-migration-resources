package org.wso2.carbon.apimgt.migration.validator.utils;

import org.wso2.carbon.apimgt.impl.APIConstants;

public class V310Utils extends Utils {
    public V310Utils(String migrateFromVersion) {
        super(migrateFromVersion);
    }

    @Override
    public String getWSDLArchivePath(String apiName, String apiVersion, String provider) {
        return APIConstants.API_WSDL_RESOURCE_LOCATION + APIConstants.API_WSDL_ARCHIVE_LOCATION + provider
                + APIConstants.WSDL_PROVIDER_SEPERATOR + apiName + apiVersion + APIConstants.ZIP_FILE_EXTENSION;
    }

    @Override
    public String getWSDLPath(String apiName, String apiVersion, String provider) {
        return APIConstants.API_WSDL_RESOURCE_LOCATION + provider + APIConstants.WSDL_PROVIDER_SEPERATOR
                + apiName + apiVersion + APIConstants.WSDL_FILE_EXTENSION;
    }
}
