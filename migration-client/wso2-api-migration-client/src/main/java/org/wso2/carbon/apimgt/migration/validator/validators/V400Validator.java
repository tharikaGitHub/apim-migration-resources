package org.wso2.carbon.apimgt.migration.validator.validators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIDefinition;
import org.wso2.carbon.apimgt.api.APIDefinitionValidationResponse;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.ErrorHandler;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.ResourceFile;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.definitions.AsyncApiParserUtil;
import org.wso2.carbon.apimgt.impl.definitions.OASParserUtil;
import org.wso2.carbon.apimgt.impl.utils.APIMWSDLReader;
import org.wso2.carbon.apimgt.impl.wsdl.model.WSDLValidationResponse;
import org.wso2.carbon.apimgt.migration.validator.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.migration.validator.utils.Utils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.PublisherCommonUtils;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.GraphQLValidationResponseDTO;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.SQLException;

public class V400Validator extends Validator {
    private static final Log log = LogFactory.getLog(V400Validator.class);

    public V400Validator(Utils utils) {
        super(utils);
    }

    @Override
    public void validateEndpoints() {
    }

    @Override
    public void validateAPIDefinition() {
        if (!utils.isStreamingAPI(apiType)) {
            validateOpenAPIDefinition();
            if (APIConstants.APITransportType.GRAPHQL.toString().equalsIgnoreCase(apiType)) {
                validateGraphQLAPIDefinition();
            } else if (APIConstants.API_TYPE_SOAP.equalsIgnoreCase(apiType)
                    || APIConstants.API_TYPE_SOAPTOREST.equalsIgnoreCase(apiType)) {
                validateWSDLDefinition();
            }
        } else {
            validateStreamingAPIDefinition();
        }
    }

    @Override
    public void validateApiAvailability() {
        try {
            log.info("Validating API availability in db for API {name: " + apiName + ", version: " +
                    apiVersion + ", provider: " + provider + "}");
            int id = ApiMgtDAO.getInstance().getAPIID(provider, apiName, apiVersion);
            if (id == -1) {
                log.error("Unable to find the API " + "{name: " + apiName + ", version: " +
                        apiVersion + ", provider: " + provider + "} in the database");
            }
        } catch (SQLException e) {
            log.error("Error while getting the database connection ", e);
        }
    }

    public void validateOpenAPIDefinition() {
        APIDefinitionValidationResponse validationResponse = null;
        String apiDefinitionString = "";
        log.info("Validating open API definition of API {name: " + apiName + ", version: " +
                apiVersion + ", provider: " + provider + "}");
        try {
            apiDefinitionString = utils.getAPIDefinition(registry, apiName, apiVersion, provider, apiId);
            validationResponse = OASParserUtil.validateAPIDefinition(apiDefinitionString, Boolean.TRUE);
        } catch (APIManagementException e) {
            log.error("Error while validating open API definition for " + apiName + " version: " + apiVersion
                    + " type: " + apiType, e);
        }
        if (validationResponse != null && !validationResponse.isValid()) {
            for (ErrorHandler error : validationResponse.getErrorItems()) {
                log.error("OpenAPI Definition for API {name: " + apiName + ", version: " +
                        apiVersion + ", provider: " + provider + "}" + " is invalid. ErrorMessage: " +
                        error.getErrorMessage() + " ErrorDescription: " + error.getErrorDescription());
            }
        } else {
            log.info("Successfully validated open API definition of " + apiName + " version: " + apiVersion
                    + " type: " + apiType);
        }

        APIDefinition apiDefinition = validationResponse.getParser();
        try {
            apiDefinition.getURITemplates(apiDefinitionString);
        } catch (APIManagementException e) {
            log.error("Error while retrieving URI Templates for " + apiName + " version: " + apiVersion
                    + " type: " + apiType, e);
        }
    }

    private void validateGraphQLAPIDefinition() {
        GraphQLValidationResponseDTO graphQLValidationResponseDTO = null;
        log.info("Validating graphQL schema definition of " + apiName + " version: " + apiVersion + " type: "
                + apiType);
        try {
            String graphqlSchema = utils.getGraphqlSchemaDefinition(registry, apiName, apiVersion, provider, apiId);
            graphQLValidationResponseDTO = PublisherCommonUtils
                    .validateGraphQLSchema("schema.graphql", graphqlSchema);
        } catch (APIManagementException e) {
            log.error(" Error while validating graphql api definition for API:" + apiName
                    + " version: " +    apiVersion + " " + e);
        }
        if (graphQLValidationResponseDTO != null && !graphQLValidationResponseDTO.isIsValid()) {
            log.error(" Invalid GraphQL definition found. " + "ErrorMessage: " + graphQLValidationResponseDTO
                    .getErrorMessage());
        } else {
            log.info("Successfully validated graphql schema of " + apiName + " version: " + apiVersion + "type: "
                    + apiType);
        }
    }

    private void validateWSDLDefinition() {
        WSDLValidationResponse wsdlValidationResponse = null;
        String wsdlArchivePath = utils.getWSDLArchivePath(apiName, apiVersion, provider);
        log.info("Validating WSDL of " + apiName + " version: " + apiVersion + " type: " + apiType);
        try {
            if (registry.resourceExists(wsdlArchivePath)) {
                byte[] wsdl = (byte[]) registry.get(wsdlArchivePath).getContent();
                InputStream targetStream = new ByteArrayInputStream(wsdl);
                wsdlValidationResponse = APIMWSDLReader.extractAndValidateWSDLArchive(targetStream);
            } else {
                String wsdlPath = utils.getWSDLPath(apiName, apiVersion, provider);
                Resource wsdl = registry.get(wsdlPath);
                ResourceFile resourceFile = new ResourceFile(wsdl.getContentStream(), wsdl.getMediaType());
                wsdlValidationResponse = APIMWSDLReader.validateWSDLFile(resourceFile.getContent());
            }
        } catch (RegistryException e) {
            log.error("Error while getting wsdl file", e);
        } catch (APIManagementException e) {
            log.error("Error while validating wsdl file of API:" + apiName + " version: " + apiVersion, e);
        }
        if (wsdlValidationResponse != null && !wsdlValidationResponse.isValid()) {
            log.error("Invalid WSDL definition found. " + wsdlValidationResponse.getError());
        } else {
            log.info("Successfully validated wsdl file of " + apiName + " version: " + apiVersion + " type: "
                    + apiType);
        }
    }

    private void validateStreamingAPIDefinition() {
        if ("4.0.0".equals(utils.getMigrateFromVersion())) {
            String apiPath = null;
            String asyncAPIDefinition = "";
            APIDefinitionValidationResponse validationResponse = null;
            log.info("Validating streaming api definition of " + apiName + " version: " + apiVersion + " type: "
                    + apiType);
            try {
                apiPath = GovernanceUtils.getArtifactPath(registry, apiId);
            } catch (GovernanceException e) {
                log.error(" Error while getting AsyncAPI definition. " + e);
            }
            if (apiPath != null) {
                int prependIndex = apiPath.lastIndexOf("/api");
                String apiSourcePath = apiPath.substring(0, prependIndex);
                String definitionPath = apiSourcePath + "/" + "asyncapi.json";
                try {
                    if (registry.resourceExists(definitionPath)) {
                        Resource apiDocResource = registry.get(definitionPath);
                        asyncAPIDefinition = new String((byte[]) apiDocResource.getContent(),
                                Charset.defaultCharset());
                    }
                } catch (RegistryException e) {
                    log.error(" Error while getting AsyncAPI definition for API: " + apiName + " version: " + apiVersion
                            , e);
                }

                if (!asyncAPIDefinition.isEmpty()) {
                    try {
                        validationResponse = AsyncApiParserUtil.validateAsyncAPISpecification(asyncAPIDefinition, true);
                    } catch (APIManagementException e) {
                        log.error(" Error while validating AsyncAPI definition for API:" + apiName + " version: "
                                + apiVersion, e);
                    }
                    if (validationResponse != null && !validationResponse.isValid()) {
                        log.error(" Invalid AsyncAPI definition found. " + validationResponse.getErrorItems());
                    }
                }
                log.info("Validating streaming api definition of API:" + apiName + " version: " + apiVersion
                        + " type: " + apiType);
            } else {
                log.error("apiPath of  " + apiName + " version: " + apiVersion + " is null");
            }
        } else {
            log.info("API " + apiName + " version: " + apiVersion + " type: " + apiType + " was not validated "
                    + "since the AsyncAPI definitions are supported after API Manager 4.0.0");
        }
    }
}
