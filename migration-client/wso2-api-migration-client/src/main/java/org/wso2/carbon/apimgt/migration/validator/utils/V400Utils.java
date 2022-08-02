package org.wso2.carbon.apimgt.migration.validator.utils;

import org.wso2.carbon.apimgt.api.model.APIRevision;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.registry.core.RegistryConstants;

public class V400Utils extends Utils {
    public V400Utils(String migrateFromVersion) {
        super(migrateFromVersion);
    }

    @Override
    public String getOpenAPIDefinitionFilePath(String apiName, String apiVersion, String apiProvider, String apiId)
            throws APIMigrationException {
        APIRevision apiRevision = APIMgtDAO.getInstance().checkAPIUUIDIsARevisionUUID(apiId);
        String resourcePath;
        if (apiRevision != null && apiRevision.getApiUUID() != null) {
            resourcePath = getRevisionPath(apiRevision);
        } else {
            resourcePath = getOASPath(apiName, apiVersion, apiProvider);
        }
        return resourcePath;
    }

    @Override
    public String getGraphqlDefinitionFilePath(String apiName, String apiVersion, String apiProvider, String apiId)
            throws APIMigrationException {
        APIRevision apiRevision = APIMgtDAO.getInstance().checkAPIUUIDIsARevisionUUID(apiId);
        String resourcePath;
        if (apiRevision != null && apiRevision.getApiUUID() != null) {
            resourcePath = getRevisionPath(apiRevision);
        } else {
            resourcePath = getGraphQLSchemaPath(apiName, apiVersion, apiProvider);
        }
        return resourcePath;
    }

    private String getOASPath(String apiName, String apiVersion, String apiProvider) {
        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + apiProvider
                + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR + apiVersion
                + RegistryConstants.PATH_SEPARATOR;
    }

    private String getGraphQLSchemaPath(String apiName, String apiVersion, String apiProvider) {
        return APIConstants.API_ROOT_LOCATION + RegistryConstants.PATH_SEPARATOR + apiProvider
                + RegistryConstants.PATH_SEPARATOR + apiName + RegistryConstants.PATH_SEPARATOR + apiVersion
                + RegistryConstants.PATH_SEPARATOR;
    }

    private String getRevisionPath(APIRevision apiRevision) {
        return APIConstants.API_REVISION_LOCATION + RegistryConstants.PATH_SEPARATOR + apiRevision.getApiUUID()
                + RegistryConstants.PATH_SEPARATOR + apiRevision.getId() + RegistryConstants.PATH_SEPARATOR;
    }
}
