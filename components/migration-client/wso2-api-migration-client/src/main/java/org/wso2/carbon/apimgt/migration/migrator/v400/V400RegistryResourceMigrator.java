/*
 * Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.apimgt.migration.migrator.v400;

import com.google.gson.Gson;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtResourceNotFoundException;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.model.*;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.dao.GatewayArtifactsMgtDAO;
import org.wso2.carbon.apimgt.impl.definitions.AsyncApiParser;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.ArtifactSaver;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.exception.ArtifactSynchronizerException;
import org.wso2.carbon.apimgt.impl.importexport.APIImportExportException;
import org.wso2.carbon.apimgt.impl.importexport.ExportFormat;
import org.wso2.carbon.apimgt.impl.importexport.ImportExportAPI;
import org.wso2.carbon.apimgt.impl.importexport.ImportExportConstants;
import org.wso2.carbon.apimgt.impl.importexport.utils.CommonUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.apimgt.migration.dto.APIInfoDTO;
import org.wso2.carbon.apimgt.migration.migrator.Utility;
import org.wso2.carbon.apimgt.migration.migrator.commonMigrators.RegistryResourceMigrator;
import org.wso2.carbon.apimgt.migration.util.APIUtil;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.apimgt.migration.util.RegistryServiceImpl;
import org.wso2.carbon.apimgt.persistence.RegistryPersistenceImpl;
import org.wso2.carbon.apimgt.persistence.exceptions.APIPersistenceException;
import org.wso2.carbon.apimgt.persistence.utils.RegistryPersistenceUtil;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.APIMappingUtil;
import org.wso2.carbon.apimgt.rest.api.publisher.v1.dto.APIProductDTO;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifactImpl;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.exceptions.ResourceNotFoundException;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.wso2.carbon.apimgt.rest.api.publisher.v1.common.mappings.ExportUtils.*;

public class V400RegistryResourceMigrator extends RegistryResourceMigrator {
    private static final Log log = LogFactory.getLog(V400RegistryResourceMigrator.class);
    private RegistryService registryService;
    List<Tenant> tenants;
    protected Registry registry;
    private TenantManager tenantManager;
    protected ArtifactSaver artifactSaver;
    private static final String OVERVIEW_PROVIDER = "overview_provider";
    private static final String OVERVIEW_VERSION = "overview_version";
    private static final String OVERVIEW_NAME = "overview_name";
    private static final String OVERVIEW_TYPE = "overview_type";
    protected GatewayArtifactsMgtDAO gatewayArtifactsMgtDAO;
    protected ImportExportAPI importExportAPI;
    protected Registry userRegistry;


    public V400RegistryResourceMigrator(String rxtDir) throws UserStoreException {
        super(rxtDir);
        tenants = loadTenants();
        registryService = new RegistryServiceImpl();
        this.artifactSaver = ServiceHolder.getArtifactSaver();
        this.importExportAPI = ServiceHolder.getImportExportService();
        this.gatewayArtifactsMgtDAO = GatewayArtifactsMgtDAO.getInstance();
    }
    APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();
    private ApiMgtDAO apiMgtDAO1 = ApiMgtDAO.getInstance();
    @Override
    public void migrate() throws APIMigrationException {
        log.info("WSO2 API-M Migration Task : Start migrating WebSocket APIs");
        migrateWebSocketAPI();
        log.info("WSO2 API-M Migration Task : Successfully migrated WebSocket APIs");

        log.info("WSO2 API-M Migration Task : Start migrating registry paths of Icon and WSDLs");
        updateRegistryPathsOfIconAndWSDL();
        log.info("WSO2 API-M Migration Task : Successfully migrated API registry paths of Icon and WSDLs.");

        log.info("WSO2 API-M Migration Task : Start removing unnecessary fault handlers[org.wso2.carbon.apimgt.usage.publisher."
                + "APIMgtFaultHandler, org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtFaultHandler] from "
                + "fault sequences");
        removeUnnecessaryFaultHandlers();
        log.info("WSO2 API-M Migration Task : Successfully removed the unnecessary fault handlers from fault sequences.");

        log.info("WSO2 API-M Migration Task : Start API Revision related migration");
        apiRevisionRelatedMigration();
        log.info("WSO2 API-M Migration Task : Successfully completed the API Revision related migration.");
    }

    public void migrateWebSocketAPI() {
        tenantManager = ServiceHolder.getRealmService().getTenantManager();
        try {
            // migrate registry artifacts
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            Map<String, String> wsUriMapping = new HashMap<>();
            for (Tenant tenant : tenants) {
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);
                try {
                    Utility.startTenantFlow(tenant.getDomain(), apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                    this.registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    GenericArtifactManager tenantArtifactManager = APIUtil
                            .getArtifactManager(this.registry, APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        if (tenantArtifacts.length > 0) {
                            log.info("WSO2 API-M Migration Task : Migrating WebSocket APIs of tenant " +
                                    tenant.getId() + '(' + tenant.getDomain() + ')');
                        }
                        for (GenericArtifact artifact : tenantArtifacts) {
                            if (StringUtils.equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE),
                                    APIConstants.APITransportType.WS.toString())) {
                                int id = Integer.parseInt(APIMgtDAO.getInstance()
                                        .getAPIID(artifact.getAttribute(Constants.API_OVERVIEW_CONTEXT)));
                                // Remove previous entries(In 3.x we are setting default REST methods with /*)
                                apiMgtDAO.removePreviousURLTemplatesForWSAPIs(id);
                                String apiIdentifier = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER) + "-" +
                                        artifact.getAttribute(APIConstants.API_OVERVIEW_NAME) + "-" +
                                        artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
                                log.info("WSO2 API-M Migration Task : Removed URL mappings for WS API: "
                                        + apiIdentifier + " with default REST " + "methods and path /*");

                                //  add default url templates for WS APIs
                                apiMgtDAO.addDefaultURLTemplatesForWSAPIs(id);
                                log.info("WSO2 API-M Migration Task : Added default WS URL mappings(SUBSCRIBE) "
                                        + "for WS API: " + apiIdentifier);

                                artifact.setAttribute(APIConstants.API_OVERVIEW_WS_URI_MAPPING,
                                        new Gson().toJson(wsUriMapping));
                                tenantArtifactManager.updateGenericArtifact(artifact);

                                API api = APIUtil.getAPI(artifact);
                                if (api != null) {
                                    AsyncApiParser asyncApiParser = new AsyncApiParser();
                                    String apiDefinition = asyncApiParser.generateAsyncAPIDefinition(api);
                                    APIProvider apiProviderTenant = APIManagerFactory.getInstance()
                                            .getAPIProvider(APIUtil.getTenantAdminUserName(tenant.getDomain()));
                                    apiProviderTenant.saveAsyncApiDefinition(api, apiDefinition);
                                    log.info("WSO2 API-M Migration Task : Generated and added Async API definition "
                                            + "for WS API: " + apiIdentifier);
                                } else {
                                    throw new APIMigrationException(
                                            "WSO2 API-M Migration Task :  Async Api definition is not added for the "
                                                    + "API " + artifact.getAttribute(
                                                    org.wso2.carbon.apimgt.impl.APIConstants.API_OVERVIEW_NAME)
                                                    + " due to returned API is null");
                                }
                            }
                        }
                    }
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while initiation the registry", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while Retrieving API artifact from the registry", e);
        } catch (APIMigrationException e) {
            log.error("WSO2 API-M Migration Task : Error while migrating WebSocket APIs", e);
        }
    }
    
    public void updateRegistryPathsOfIconAndWSDL() {
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                log.info("WSO2 API-M Migration Task : Updating Registry paths of API icons and WSDLs of "
                        + "tenant " + tenant.getId() + '(' +
                        tenant.getDomain() + ')');
                List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);

                try {
                    Utility.startTenantFlow(tenant.getDomain(), apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                    this.registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    GenericArtifactManager tenantArtifactManager = APIUtil
                            .getArtifactManager(this.registry, APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        for (GenericArtifact artifact : tenantArtifacts) {
                            if (artifact != null) {
                                APIInfoDTO apiInfoDTO = new APIInfoDTO();
                                apiInfoDTO.setUuid(artifact.getId());
                                apiInfoDTO.setApiProvider(
                                        APIUtil.replaceEmailDomainBack(artifact.getAttribute(OVERVIEW_PROVIDER)));
                                apiInfoDTO.setApiName(artifact.getAttribute(OVERVIEW_NAME));
                                apiInfoDTO.setApiVersion(artifact.getAttribute(OVERVIEW_VERSION));
                                apiInfoDTOList.add(apiInfoDTO);
                            }
                        }
                        for (APIInfoDTO apiInfoDTO : apiInfoDTOList) {
                            String apiId = apiInfoDTO.getApiProvider() + "-" + apiInfoDTO.getApiName() + "-" +
                                    apiInfoDTO.getApiVersion();
                            String apiPath = GovernanceUtils.getArtifactPath(registry, apiInfoDTO.getUuid());
                            int prependIndex = apiPath.lastIndexOf("/api");
                            String artifactPath = apiPath.substring(0, prependIndex);
                            String artifactOldPathIcon =
                                    APIConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR + apiInfoDTO
                                            .getApiProvider() + RegistryConstants.PATH_SEPARATOR + apiInfoDTO
                                            .getApiName() + RegistryConstants.PATH_SEPARATOR + apiInfoDTO
                                            .getApiVersion() + RegistryConstants.PATH_SEPARATOR
                                            + APIConstants.API_ICON_IMAGE;
                            if (registry.resourceExists(artifactOldPathIcon)) {
                                Resource resource = registry.get(artifactOldPathIcon);
                                String thumbPath =
                                        artifactPath + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;
                                registry.put(thumbPath, resource);
                                GenericArtifact apiArtifact = tenantArtifactManager
                                        .getGenericArtifact(apiInfoDTO.getUuid());
                                apiArtifact.setAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL, thumbPath);
                                tenantArtifactManager.updateGenericArtifact(apiArtifact);
                                log.info("WSO2 API-M Migration Task : Updated " +
                                        APIConstants.API_OVERVIEW_THUMBNAIL_URL + " of API: " + apiId + " to " +
                                        thumbPath);
                            }
                            String wsdlResourcePathOld =
                                    APIConstants.API_WSDL_RESOURCE_LOCATION + RegistryPersistenceUtil
                                            .createWsdlFileName(RegistryPersistenceUtil
                                                            .replaceEmailDomain(apiInfoDTO.getApiProvider()),
                                                    apiInfoDTO.getApiName(), apiInfoDTO.getApiVersion());
                            APIIdentifier identifier = new APIIdentifier(RegistryPersistenceUtil
                                    .replaceEmailDomain(apiInfoDTO.getApiProvider()),
                                    apiInfoDTO.getApiName(), apiInfoDTO.getApiVersion());
                            String wsdlResourceArchivePathOld = RegistryPersistenceUtil.getWsdlArchivePath(identifier);
                            String resourcePath = null;
                            if (registry.resourceExists(wsdlResourcePathOld)) {
                                resourcePath = wsdlResourcePathOld;
                            } else if (registry.resourceExists(wsdlResourceArchivePathOld)) {
                                resourcePath = wsdlResourceArchivePathOld;
                            }
                            if (resourcePath != null) {
                                log.info("WSDL resource path: " + resourcePath);
                                Resource resource = registry.get(resourcePath);
                                String wsdlResourcePath;
                                String wsdlResourcePathArchive = artifactPath + RegistryConstants.PATH_SEPARATOR
                                        + APIConstants.API_WSDL_ARCHIVE_LOCATION + RegistryPersistenceUtil
                                        .replaceEmailDomain(apiInfoDTO.getApiProvider())
                                        + APIConstants.WSDL_PROVIDER_SEPERATOR + apiInfoDTO.getApiName() + apiInfoDTO
                                        .getApiVersion() + APIConstants.ZIP_FILE_EXTENSION;
                                String wsdlResourcePathFile =
                                        artifactPath + RegistryConstants.PATH_SEPARATOR + RegistryPersistenceUtil
                                                .createWsdlFileName(RegistryPersistenceUtil
                                                                .replaceEmailDomain(apiInfoDTO.getApiProvider()),
                                                        apiInfoDTO.getApiName(), apiInfoDTO.getApiVersion());
                                if (resource.getPath() != null
                                        && resource.getPath().endsWith(APIConstants.ZIP_FILE_EXTENSION)) {
                                    wsdlResourcePath = wsdlResourcePathArchive;
                                    if ("application/octet-stream".equals(resource.getMediaType())) {
                                        resource.setMediaType(APIConstants.APPLICATION_ZIP);
                                        registry.put(resourcePath, resource);
                                    }
                                } else {
                                    wsdlResourcePath = wsdlResourcePathFile;
                                }
                                registry.copy(resourcePath, wsdlResourcePath);
                                GenericArtifact apiArtifact = tenantArtifactManager
                                        .getGenericArtifact(apiInfoDTO.getUuid());
                                String absoluteWSDLResourcePath = RegistryUtils
                                        .getAbsolutePath(RegistryContext.getBaseInstance(),
                                                RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + wsdlResourcePath;
                                String wsdlRegistryPath =
                                        RegistryConstants.PATH_SEPARATOR + "registry" + RegistryConstants.PATH_SEPARATOR
                                                + "resource" + absoluteWSDLResourcePath;
                                apiArtifact.setAttribute(APIConstants.API_OVERVIEW_WSDL, wsdlRegistryPath);
                                tenantArtifactManager.updateGenericArtifact(apiArtifact);
                                log.info("WSO2 API-M Migration Task : Updated " + APIConstants.API_OVERVIEW_WSDL +
                                        " of API: " + apiId + " to " + wsdlRegistryPath);
                            }
                        }
                    }
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while initiation the registry", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while Retrieving API artifact from the registry", e);
        }
    }

    public void apiRevisionRelatedMigration() throws APIMigrationException {
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                log.info("WSO2 API-M Migration Task : Starting API Revision related migration for tenant " +
                        tenant.getId() + '(' + tenant.getDomain() + ')');
                List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
                List<Environment> dynamicEnvironments = org.wso2.carbon.apimgt.migration.
                        migrator.v400.dao.ApiMgtDAO.getInstance().getAllEnvironments(tenant.getDomain());
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);

                try {
                    Utility.startTenantFlow(tenant.getDomain(), apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                    this.registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    this.userRegistry = ServiceHolder.getRegistryService()
                            .getGovernanceUserRegistry(APIUtil.getTenantAdminUserName(tenant.getDomain()), apiTenantId);
                    GenericArtifactManager tenantArtifactManager = APIUtil
                            .getArtifactManager(this.registry, APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        APIProvider apiProviderTenant = APIManagerFactory.getInstance()
                                .getAPIProvider(APIUtil.getTenantAdminUserName(tenant.getDomain()));
                        for (GenericArtifact artifact : tenantArtifacts) {
                            String artifactPath = ((GenericArtifactImpl) artifact).getArtifactPath();
                            if (artifactPath.contains("/apimgt/applicationdata/apis/")) {
                                continue;
                            }
                            if (!StringUtils.equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS),
                                    org.wso2.carbon.apimgt.impl.APIConstants.CREATED) && !StringUtils
                                    .equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS),
                                            org.wso2.carbon.apimgt.impl.APIConstants.RETIRED)) {
                                APIInfoDTO apiInfoDTO = new APIInfoDTO();
                                apiInfoDTO.setUuid(artifact.getId());
                                apiInfoDTO.setApiProvider(
                                        APIUtil.replaceEmailDomainBack(artifact.getAttribute(OVERVIEW_PROVIDER)));
                                apiInfoDTO.setApiName(artifact.getAttribute(OVERVIEW_NAME));
                                apiInfoDTO.setApiVersion(artifact.getAttribute(OVERVIEW_VERSION));
                                apiInfoDTO.setType(artifact.getAttribute(OVERVIEW_TYPE));
                                //apiInfoDTO.setOrganization(api.getOrganization());
                                apiInfoDTOList.add(apiInfoDTO);
                            }
                        }
                        for (APIInfoDTO apiInfoDTO : apiInfoDTOList) {
                            //adding the revision
                            APIRevision apiRevision = new APIRevision();
                            apiRevision.setApiUUID(apiInfoDTO.getUuid());
                            apiRevision.setDescription("Initial revision created in migration process");
                            String revisionId;
                            if (!StringUtils.equalsIgnoreCase(apiInfoDTO.getType(), APIConstants.API_PRODUCT)) {
                                revisionId = addAPIRevision(apiRevision, tenant, apiProviderTenant,
                                        tenantArtifactManager);
                            } else {
                                revisionId = addAPIProductRevision(apiRevision, tenant, apiProviderTenant,
                                        tenantArtifactManager);
                            }
                            // retrieve api artifacts
                            GenericArtifact apiArtifact = tenantArtifactManager
                                    .getGenericArtifact(apiInfoDTO.getUuid());
                            List<APIRevisionDeployment> apiRevisionDeployments = new ArrayList<>();
                            String environments = apiArtifact.getAttribute(APIConstants.API_OVERVIEW_ENVIRONMENTS);
                            String[] arrOfEnvironments = environments.split(",");
                            for (String environment : arrOfEnvironments) {
                                APIRevisionDeployment apiRevisionDeployment = new APIRevisionDeployment();
                                apiRevisionDeployment.setRevisionUUID(revisionId);
                                apiRevisionDeployment.setDeployment(environment);
                                // Null VHost for the environments defined in deployment.toml (the default vhost)
                                apiRevisionDeployment.setVhost(null);
                                apiRevisionDeployment.setDisplayOnDevportal(true);
                                apiRevisionDeployments.add(apiRevisionDeployment);
                            }

                            String[] labels = apiArtifact.getAttributes(APIConstants.API_LABELS_GATEWAY_LABELS);
                            if (labels != null) {
                                for (String label : labels) {
                                    if (Arrays.stream(arrOfEnvironments)
                                            .anyMatch(tomlEnv -> StringUtils.equals(tomlEnv, label))) {
                                        // if API is deployed to an environment and label with the same name,
                                        // discard deployment to dynamic environment
                                        continue;
                                    }
                                    Optional<Environment> optionalEnv = dynamicEnvironments.stream()
                                            .filter(e -> StringUtils.equals(e.getName(), label)).findFirst();
                                    Environment dynamicEnv = optionalEnv.orElseThrow(() -> new APIMigrationException(
                                            "Error while retrieving dynamic environment of the label: " + label));
                                    List<VHost> vhosts = dynamicEnv.getVhosts();
                                    if (vhosts.size() > 0) {
                                        APIRevisionDeployment apiRevisionDeployment = new APIRevisionDeployment();
                                        apiRevisionDeployment.setRevisionUUID(revisionId);
                                        apiRevisionDeployment.setDeployment(dynamicEnv.getName());
                                        apiRevisionDeployment.setVhost(vhosts.get(0).getHost());
                                        apiRevisionDeployment.setDisplayOnDevportal(true);
                                        apiRevisionDeployments.add(apiRevisionDeployment);
                                    } else {
                                        throw new APIMigrationException(
                                                "WSO2 API-M Migration Task : Vhosts are empty for the dynamic "
                                                        + "environment: " + dynamicEnv.getName());
                                    }
                                }
                            }

                            if (!apiRevisionDeployments.isEmpty()) {
                                if (!StringUtils.equalsIgnoreCase(apiInfoDTO.getType(), APIConstants.API_PRODUCT)) {
                                    apiProviderTenant
                                            .deployAPIRevision(apiInfoDTO.getUuid(), revisionId, apiRevisionDeployments,
                                                    tenant.getDomain());
                                } else {
                                    apiProviderTenant.deployAPIProductRevision(apiInfoDTO.getUuid(), revisionId,
                                            apiRevisionDeployments);
                                }
                            }
                        }
                    }
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while initiation the registry", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while Retrieving API artifact from the registry", e);
        }
    }

    private String addAPIRevision(APIRevision apiRevision, Tenant tenant, APIProvider apiProviderTenant,
                                  GenericArtifactManager artifactManager) throws APIMigrationException {

        int revisionId = 0;
        String organization = tenant.getDomain();
        try {
            revisionId = apiMgtDAO1.getMostRecentRevisionId(apiRevision.getApiUUID()) + 1;
        } catch (APIManagementException e) {
            log.warn("WSO2 API-M Migration Task : Couldn't retrieve mose recent revision Id from revision UUID: "
                    + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        }
        apiRevision.setId(revisionId);
        APIIdentifier apiId;
        try {
            apiId = APIUtil.getAPIIdentifierFromUUID(apiRevision.getApiUUID());
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Couldn't retrieve API Identifier for from "
                    + "revision UUID: " + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '(' +
                    tenant.getDomain() + ')');
        }
        if (apiId == null) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Couldn't retrieve existing API with API "
                    + "UUID: " + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain()
                    + ')');
        }
        apiId.setUuid(apiRevision.getApiUUID());
        String revisionUUID;
        try {
            revisionUUID = addAPIRevisionToRegistry(apiId.getUUID(), revisionId, tenant.getDomain(), artifactManager);
        } catch (APIPersistenceException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to add revision registry artifacts for"
                    + " API: " + apiId.getUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        }
        if (StringUtils.isEmpty(revisionUUID)) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to retrieve revision from registry "
                    + "artifacts for API: " + apiId.getUUID()+ " for tenant " + tenant.getId() + '('
                    + tenant.getDomain() + ')');
        } else {
            log.info("WSO2 API-M Migration Task : Successfully added revision: "
                    + revisionUUID + " to registry for API: " + apiId.getUUID() + " for tenant " + tenant.getId()
                    + '(' + tenant.getDomain() + ')');
        }

        apiRevision.setRevisionUUID(revisionUUID);

        try {
            apiMgtDAO1.addAPIRevision(apiRevision);
            log.info("WSO2 API-M Migration Task : Successfully added revision: " + revisionUUID + " to database for"
                    + " API: " + apiId.getUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to add revision to database artifacts "
                    + "for API: " + apiId.getUUID() + " revision uuid: " + revisionUUID + " for tenant "
                    + tenant.getId() + '(' + tenant.getDomain() + ')');
        }

        log.info("WSO2 API-M Migration Task : Storing revision artifacts of API: " + apiRevision.getApiUUID() +
                " into gateway artifacts " + "and external server for tenant " + tenant.getId() + '(' +
                tenant.getDomain() + ')');

        try {
            File artifact = importExportAPI
                    .exportAPI(apiRevision.getApiUUID(), revisionUUID, true, ExportFormat.JSON, false, true,
                            organization);
            // Keeping the organization as tenant domain since MG does not support organization-wise deployment
            // Artifacts will be deployed in ST for all organizations
            gatewayArtifactsMgtDAO
                    .addGatewayAPIArtifactAndMetaData(apiRevision.getApiUUID(), apiId.getApiName(), apiId.getVersion(),
                            apiRevision.getRevisionUUID(), organization,
                            org.wso2.carbon.apimgt.impl.APIConstants.HTTP_PROTOCOL, artifact);
            if (artifactSaver != null) {
                // Keeping the organization as tenant domain since MG does not support organization-wise deployment
                // Artifacts will be deployed in ST for all organizations
                artifactSaver.saveArtifact(apiRevision.getApiUUID(), apiId.getApiName(), apiId.getVersion(),
                        apiRevision.getRevisionUUID(), organization, artifact);
            }
            log.info("WSO2 API-M Migration Task : Successfully added revision artifact of API: " + apiId.getUUID()
                    + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        } catch (APIImportExportException | ArtifactSynchronizerException | APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error while Store the Revision Artifact "
                    + "for API: " + apiId.getUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
        }
        return revisionUUID;
    }

    private String addAPIProductRevision(APIRevision apiRevision, Tenant tenant, APIProvider apiProviderTenant,
                                         GenericArtifactManager artifactManager) throws APIMigrationException {

        int revisionId = 0;
        String organization = tenant.getDomain();

        try {
            revisionId = apiMgtDAO1.getMostRecentRevisionId(apiRevision.getApiUUID()) + 1;
        } catch (APIManagementException e) {
            log.warn("WSO2 API-M Migration Task : Couldn't retrieve mose recent revision Id from revision UUID: "
                    + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        }
        apiRevision.setId(revisionId);
        APIProductIdentifier apiProductIdentifier;

        try {
            apiProductIdentifier = APIUtil.getAPIProductIdentifierFromUUID(apiRevision.getApiUUID());
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Couldn't retrieve APIProduct identifier for "
                    + "API product: " + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '('
                    + tenant.getDomain() + ')');
        }

        if (apiProductIdentifier == null) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Couldn't retrieve existing API Product with "
                    + "ID: " + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain()
                    + ')');
        }
        apiProductIdentifier.setUUID(apiRevision.getApiUUID());
        String revisionUUID;
        try {
            revisionUUID = addAPIRevisionToRegistry(apiProductIdentifier.getUUID(), revisionId, tenant.getDomain(),
                    artifactManager);
        } catch (APIPersistenceException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to add revision registry artifacts for"
                    + " API Product: " + apiRevision.getApiUUID() + " for tenant " + tenant.getId() + '('
                    + tenant.getDomain() + ')');
        }

        if (StringUtils.isEmpty(revisionUUID)) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to retrieve revision registry artifacts"
                    + " for API Product uuid: " + apiRevision.getApiUUID() + " revision uuid: " + revisionUUID
                    + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        } else {
            log.info("WSO2 API-M Migration Task : Successfully added revision: " + revisionUUID + " to registry for"
                    + " API Product: " + apiProductIdentifier.getUUID() + "...for tenant " + tenant.getId()
                    + '(' + tenant.getDomain() + ')');
            apiRevision.setRevisionUUID(revisionUUID);
        }

        try {
            org.wso2.carbon.apimgt.migration.
                    migrator.v400.dao.ApiMgtDAO.getInstance().addAPIProductRevision(apiRevision);
            log.info("WSO2 API-M Migration Task : Successfully added revision: " + revisionUUID + " to database for"
                    + " API Product: " + apiProductIdentifier.getUUID() + " for tenant " + tenant.getId() + '('
                    + tenant.getDomain() + ')');
        } catch (APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to add API revision uuid: "
                    + revisionUUID + " " + "for API Product : " + apiProductIdentifier.getUUID() + " for tenant "
                    + tenant.getId() + '(' + tenant.getDomain() + ')');
        }

        log.info("WSO2 API-M Migration Task : Storing revision artifacts of API Product: " + apiRevision.getApiUUID()
                + " into gateway artifacts and external server for tenant " + tenant.getId() +
                '(' + tenant.getDomain() + ')');

        // Storing revision artifacts of API Product for gateway artifacts and external server
        try {
            File artifact = exportAPIProduct(apiRevision.getApiUUID(), revisionUUID,
                    true, ExportFormat.JSON, false, true, tenant,
                    apiProviderTenant);

            gatewayArtifactsMgtDAO
                    .addGatewayAPIArtifactAndMetaData(apiRevision.getApiUUID(), apiProductIdentifier.getName(),
                            apiProductIdentifier.getVersion(), apiRevision.getRevisionUUID(), organization,
                            org.wso2.carbon.apimgt.impl.APIConstants.API_PRODUCT, artifact);
            if (artifactSaver != null) {
                artifactSaver.saveArtifact(apiRevision.getApiUUID(), apiProductIdentifier.getName(),
                        apiProductIdentifier.getVersion(), apiRevision.getRevisionUUID(), organization, artifact);
            }
            log.info("WSO2 API-M Migration Task : Successfully added revision artifact of API Product: " +
                    apiProductIdentifier.getUUID() + " for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')');
        } catch (APIImportExportException | ArtifactSynchronizerException | APIManagementException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error while Store the Revision Artifact for "
                    + "API product: " + apiProductIdentifier.getUUID() + " for tenant " + tenant.getId() + '('
                    + tenant.getDomain() + ')', e);
        }
        return revisionUUID;
    }

    public void removeUnnecessaryFaultHandlers() {
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);
                try {
                    Utility.startTenantFlow(tenant.getDomain(), apiTenantId, MultitenantUtils
                            .getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                    this.registry = ServiceHolder.getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                    // Fault Handlers that needs to be removed from fault sequences
                    String unnecessaryFaultHandler1 = "org.wso2.carbon.apimgt.usage.publisher.APIMgtFaultHandler";
                    String unnecessaryFaultHandler2 = "org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtFaultHandler";
                    org.wso2.carbon.registry.api.Collection seqCollection = null;
                    String faultSequencePath = org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_LOCATION
                            + RegistryConstants.PATH_SEPARATOR
                            + org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT;

                    try {
                        seqCollection = (org.wso2.carbon.registry.api.Collection) registry.get(faultSequencePath);
                    } catch (ResourceNotFoundException e) {
                        log.warn("WSO2 API-M Migration Task : Resource does not exist for " + faultSequencePath
                                + " for tenant domain:" + tenant.getDomain());
                    }

                    if (seqCollection != null) {
                        String[] childPaths = seqCollection.getChildren();
                        for (String childPath : childPaths) {
                            // Retrieve fault sequence from registry
                            Resource sequence = registry.get(childPath);
                            DocumentBuilderFactory factory = APIUtil.getSecuredDocumentBuilder();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            String content = new String((byte[]) sequence.getContent(), Charset.defaultCharset());
                            Document doc = builder.parse(new InputSource(new StringReader(content)));
                            // Retrieve elements with the tag name of "class" since the fault handlers that needs to
                            // be removed are located within "class" tags
                            NodeList list = doc.getElementsByTagName("class");
                            for (int i = 0; i < list.getLength(); i++) {
                                Node node = (Node) list.item(i);
                                // Retrieve the element with "name" attribute to identify the fault handlers to be removed
                                NamedNodeMap attr = node.getAttributes();
                                Node namedItem = null;
                                if (null != attr) {
                                    namedItem = attr.getNamedItem("name");
                                }
                                // Remove the relevant fault handlers
                                if (unnecessaryFaultHandler1.equals(namedItem.getNodeValue())
                                        || unnecessaryFaultHandler2.equals(namedItem.getNodeValue())) {
                                    Node parentNode = node.getParentNode();
                                    parentNode.removeChild(node);
                                    parentNode.normalize();
                                    log.info("WSO2 API-M Migration Task : Removed " + namedItem.getNodeValue() +
                                            " from sequence: " + childPath);
                                }
                            }
                            // Convert the content to String
                            String newContent = Utility.toString(doc);
                            // Update the registry with the new content
                            sequence.setContent(newContent);
                            registry.put(childPath, sequence);
                        }
                    }
                } finally {
                    PrivilegedCarbonContext.endTenantFlow();
                }
            }
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving tenant admin's username", e);
        } catch (org.wso2.carbon.registry.api.RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving fault sequences", e);
        } catch (Exception e) {
            log.error("WSO2 API-M Migration Task : Error while removing unnecessary fault handlers from fault sequences", e);
        }
    }

    private String addAPIRevisionToRegistry(String apiUUID, int revisionId, String organization,
                                            GenericArtifactManager artifactManager)
            throws APIPersistenceException {
        String revisionUUID;
        boolean transactionCommitted = false;
        boolean tenantFlowStarted = false;
        try {
            GenericArtifact apiArtifact = artifactManager.getGenericArtifact(apiUUID);
            if (apiArtifact != null) {
                API api = RegistryPersistenceUtil.getApiForPublishing(registry, apiArtifact);
                APIIdentifier apiId = api.getId();
                String apiPath = RegistryPersistenceUtil.getAPIPath(apiId);
                int prependIndex = apiPath.lastIndexOf("/api");
                String apiSourcePath = apiPath.substring(0, prependIndex);
                String revisionTargetPath = RegistryPersistenceUtil.getRevisionPath(apiId.getUUID(), revisionId);
                if (registry.resourceExists(revisionTargetPath)) {
                    log.warn("WSO2 API-M Migration Task : API revision already exists with id: " + revisionId);
                } else {
                    registry.copy(apiSourcePath, revisionTargetPath);
                    registry.commitTransaction();
                    transactionCommitted = true;

                    String logMessage = "WSO2 API-M Migration Task : Revision for API Name: " + apiId.getApiName()
                            + ", " + "API Version " + apiId.getVersion() + " created";
                    log.info(logMessage);

                }
                Resource apiRevisionArtifact = registry.get(revisionTargetPath + "api");
                revisionUUID = apiRevisionArtifact.getUUID();

            } else {
                String msg = "WSO2 API-M Migration Task : Failed to get API. API artifact corresponding to artifactId "
                        + apiUUID + " does not exist";
                throw new APIMgtResourceNotFoundException(msg);
            }
        } catch (RegistryException e) {
            try {
                registry.rollbackTransaction();
            } catch (RegistryException re) {
                // Throwing an error here would mask the original exception
                log.error("WSO2 API-M Migration Task : Error while rolling back the transaction for API Revision "
                        + "create for API: " + apiUUID, re);
            }
            throw new APIPersistenceException("WSO2 API-M Migration Task : Error while performing registry transaction"
                    + " operation", e);
        } catch (APIManagementException e) {
            throw new APIPersistenceException("WSO2 API-M Migration Task : Error while creating API Revision", e);
        } finally {
            try {
                if (tenantFlowStarted) {
                    RegistryPersistenceUtil.endTenantFlow();
                }
                if (!transactionCommitted) {
                    registry.rollbackTransaction();
                }
            } catch (RegistryException ex) {
                throw new APIPersistenceException("WSO2 API-M Migration Task : Error while rolling back the transaction"
                        + " for API Revision create for API: " + apiUUID, ex);
            }
        }
        return revisionUUID;
    }

    private File exportAPIProduct(String apiId, String revisionUUID, boolean preserveStatus, ExportFormat format,
                                  boolean preserveDocs, boolean preserveCredentials, Tenant tenant, APIProvider apiProvider)
            throws APIManagementException, APIImportExportException, APIMigrationException {

        String organization = tenant.getDomain();
        APIProductIdentifier apiProductIdentifier = APIUtil.getAPIProductIdentifierFromUUID(apiId);
        apiProductIdentifier.setUUID(apiId);
        APIProduct product = apiProvider.getAPIProductbyUUID(revisionUUID, organization);
        APIProductDTO apiProductDtoToReturn = APIMappingUtil.fromAPIProducttoDTO(product);
        return exportApiProduct(apiProvider, apiProductIdentifier, apiProductDtoToReturn, tenant, format,
                preserveStatus, preserveDocs, preserveCredentials, organization);
    }

    private File exportApiProduct(APIProvider apiProvider, APIProductIdentifier apiProductIdentifier,
                                  APIProductDTO apiProductDtoToReturn, Tenant tenant, ExportFormat exportFormat, Boolean preserveStatus,
                                  boolean preserveDocs, boolean preserveCredentials, String organization)
            throws APIManagementException, APIImportExportException, APIMigrationException {

        // Create temp location for storing API Product data

        String username =  MultitenantUtils.getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain()));
        File exportFolder = CommonUtil.createTempDirectory(apiProductIdentifier);
        String exportAPIBasePath = exportFolder.toString();
        String archivePath = exportAPIBasePath
                .concat(File.separator + apiProductIdentifier.getName() + "-" + apiProductIdentifier.getVersion());

        CommonUtil.createDirectory(archivePath);

        if (preserveDocs) {
            addThumbnailToArchive(archivePath, apiProductIdentifier, apiProvider);
            addDocumentationToArchive(archivePath, apiProductIdentifier, exportFormat, apiProvider,
                    org.wso2.carbon.apimgt.impl.APIConstants.API_PRODUCT_IDENTIFIER_TYPE);
        }
        // Set API Product status to created if the status is not preserved
        if (!preserveStatus) {
            apiProductDtoToReturn.setState(APIProductDTO.StateEnum.CREATED);
        }
        addGatewayEnvironmentsToArchive(archivePath, apiProductDtoToReturn.getId(), exportFormat, apiProvider);

        try {
            addAPIProductMetaInformationToArchive(archivePath, apiProductDtoToReturn, exportFormat);
        } catch (APIMigrationException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to add meta information for API Product: "
                    + apiProductDtoToReturn.getName() , e);
        }
        addDependentAPIsToArchive(archivePath, apiProductDtoToReturn, exportFormat, apiProvider, username,
                Boolean.TRUE, preserveDocs, preserveCredentials, organization);

        // Export mTLS authentication related certificates

        log.info("WSO2 API-M Migration Task : Exporting client certificates.");
        addClientCertificatesToArchive(archivePath, apiProductIdentifier, tenant.getId(), apiProvider, exportFormat,
                organization);

        CommonUtil.archiveDirectory(exportAPIBasePath);
        FileUtils.deleteQuietly(new File(exportAPIBasePath));
        return new File(exportAPIBasePath + org.wso2.carbon.apimgt.impl.APIConstants.ZIP_FILE_EXTENSION);
    }

    private void addAPIProductMetaInformationToArchive(String archivePath, APIProductDTO apiProductDtoToReturn,
                                                       ExportFormat exportFormat) throws APIImportExportException, APIMigrationException {

        CommonUtil.createDirectory(archivePath + File.separator + ImportExportConstants.DEFINITIONS_DIRECTORY);

        try {
            String formattedSwaggerJson = getAPIDefinitionOfAPIProduct(
                    APIMappingUtil.fromDTOtoAPIProduct(apiProductDtoToReturn, apiProductDtoToReturn.getProvider()));
            CommonUtil.writeToYamlOrJson(archivePath + ImportExportConstants.SWAGGER_DEFINITION_LOCATION, exportFormat,
                    formattedSwaggerJson);

            log.info("WSO2 API-M Migration Task : Successfully retrieved meta information for API Product: "
                                + apiProductDtoToReturn.getName());

            CommonUtil.writeDtoToFile(archivePath + ImportExportConstants.API_PRODUCT_FILE_LOCATION, exportFormat,
                    ImportExportConstants.TYPE_API_PRODUCT, apiProductDtoToReturn);
        } catch (APIManagementException | APIMigrationException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error while retrieving Swagger definition"
                    + " for API Product: " + apiProductDtoToReturn.getName(), e);
        } catch (IOException e) {
            throw new APIImportExportException("WSO2 API-M Migration Task : Error while saving as YAML for API "
                    + "Product: " + apiProductDtoToReturn.getName(), e);
        }
    }

    private String getAPIDefinitionOfAPIProduct(APIProduct product) throws APIMigrationException {

        String resourcePath = APIUtil.getAPIProductOpenAPIDefinitionFilePath(product.getId());
        JSONParser parser = new JSONParser();
        String apiDocContent = null;

        try {
            if (this.userRegistry.resourceExists
                    (resourcePath + org.wso2.carbon.apimgt.impl.APIConstants.API_OAS_DEFINITION_RESOURCE_NAME)) {
                Resource apiDocResource = this.userRegistry
                        .get(resourcePath + org.wso2.carbon.apimgt.impl.APIConstants.API_OAS_DEFINITION_RESOURCE_NAME);
                apiDocContent = new String((byte[]) apiDocResource.getContent(), Charset.defaultCharset());
                parser.parse(apiDocContent);
            } else {
                log.info("WSO2 API-M Migration Task : Resource " +
                            org.wso2.carbon.apimgt.impl.APIConstants.API_OAS_DEFINITION_RESOURCE_NAME +
                            " not found at " + resourcePath);
            }
        } catch (RegistryException | ParseException e) {
            throw new APIMigrationException ("WSO2 API-M Migration Task : Error while retrieving OpenAPI v2.0 or v3.0.0"
                    + " Definition for " + product.getId().getName() + '-' + product.getId().getProviderName(), e);
        }
        return apiDocContent;
    }

}
