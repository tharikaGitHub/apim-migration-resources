/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.API;
import org.wso2.carbon.apimgt.api.model.APIProduct;
import org.wso2.carbon.apimgt.api.APIProvider;
import org.wso2.carbon.apimgt.api.model.APIIdentifier;
import org.wso2.carbon.apimgt.api.model.APIRevision;
import org.wso2.carbon.apimgt.api.model.APIRevisionDeployment;
import org.wso2.carbon.apimgt.api.model.Environment;
import org.wso2.carbon.apimgt.api.model.VHost;
import org.wso2.carbon.apimgt.impl.certificatemgt.ResponseCode;
import org.wso2.carbon.apimgt.impl.dao.ApiMgtDAO;
import org.wso2.carbon.apimgt.impl.definitions.AsyncApiParser;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.APIManagerFactory;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.sp_migration.APIMStatMigrationException;
import org.wso2.carbon.apimgt.migration.dao.APIMgtDAO;
import org.wso2.carbon.apimgt.migration.dto.*;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.apimgt.migration.util.TenantUtil;
import org.wso2.carbon.apimgt.persistence.APIConstants;
import org.wso2.carbon.apimgt.persistence.utils.RegistryPersistenceUtil;
import org.wso2.carbon.apimgt.persistence.exceptions.APIPersistenceException;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.api.util.GovernanceUtils;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.exceptions.ResourceNotFoundException;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.utils.RegistryUtils;
import org.wso2.carbon.registry.core.config.RegistryContext;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.google.gson.Gson;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class MigrateFrom320 extends MigrationClientBase implements MigrationClient {

    private static final Log log = LogFactory.getLog(MigrateFrom320.class);
    private static final String SEPERATOR = "/";
    private static final String SPLITTER = ":";
    private static final String TENANT_IDENTIFIER = "t";
    private static char[] TRUST_STORE_PASSWORD = System.getProperty("javax.net.ssl.trustStorePassword").toCharArray();
    private static String TRUST_STORE = System.getProperty("javax.net.ssl.trustStore");
    private static String CERTIFICATE_TYPE = "X.509";
    public static final String BEGIN_CERTIFICATE_STRING = "-----BEGIN CERTIFICATE-----\n";
    public static final String END_CERTIFICATE_STRING = "-----END CERTIFICATE-----";
    private static final String KEY_STORE_TYPE = "JKS";
    private static final String APPLICATION_ROLE_PREFIX = "Application/";
    private RegistryService registryService;
    protected Registry registry;
    private TenantManager tenantManager;
    APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();
    private List<Tenant> tenants;

    public MigrateFrom320(String tenantArguments, String blackListTenantArguments, String tenantRange,
                          RegistryService registryService, TenantManager tenantManager) throws UserStoreException {
        super(tenantArguments, blackListTenantArguments, tenantRange, tenantManager);
        this.registryService = registryService;
        this.tenantManager = tenantManager;
        this.tenants = APIUtil.getAllTenantsWithSuperTenant();
    }

    @Override
    public void databaseMigration() throws APIMigrationException, SQLException {
    }

    @Override
    public void registryResourceMigration() throws APIMigrationException {
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
    }

    @Override
    public void spMigration() throws APIMigrationException {
    }

    @Override
    public void updateAPITypeInDB() throws APIMigrationException {
    }

    @Override
    public void updateAPIPropertyVisibility() {
    }

    public void updateRegistryPathsOfIconAndWSDL() throws APIMigrationException {
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                log.info("WSO2 API-M Migration Task : Updating Registry paths of API icons and WSDLs of "
                        + "tenant " + + tenant.getId() + '(' + tenant.getDomain() + ')');
                List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);
                startTenantFlow(tenant.getDomain(), apiTenantId,
                        MultitenantUtils.getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                this.registry = ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                GenericArtifactManager tenantArtifactManager = APIUtil.getArtifactManager(this.registry,
                        APIConstants.API_KEY);
                if (tenantArtifactManager != null) {
                    GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                    for (GenericArtifact artifact : tenantArtifacts) {
                        API api = APIUtil.getAPI(artifact);
                        if (api != null) {
                            APIInfoDTO apiInfoDTO = new APIInfoDTO();
                            apiInfoDTO.setUuid(api.getUUID());
                            apiInfoDTO.setApiProvider(APIUtil.replaceEmailDomainBack(api.getId().getProviderName()));
                            apiInfoDTO.setApiName(api.getId().getApiName());
                            apiInfoDTO.setApiVersion(api.getId().getVersion());
                            apiInfoDTOList.add(apiInfoDTO);
                        }
                    }
                    for (APIInfoDTO apiInfoDTO : apiInfoDTOList) {
                        String apiPath = GovernanceUtils.getArtifactPath(registry, apiInfoDTO.getUuid());
                        int prependIndex = apiPath.lastIndexOf("/api");
                        String artifactPath = apiPath.substring(0, prependIndex);
                        String artifactOldPathIcon = APIConstants.API_IMAGE_LOCATION + RegistryConstants.PATH_SEPARATOR
                                + apiInfoDTO.getApiProvider() + RegistryConstants.PATH_SEPARATOR + apiInfoDTO.getApiName()
                                + RegistryConstants.PATH_SEPARATOR + apiInfoDTO.getApiVersion()
                                + RegistryConstants.PATH_SEPARATOR + APIConstants.API_ICON_IMAGE;
                        if (registry.resourceExists(artifactOldPathIcon)) {
                            Resource resource = registry.get(artifactOldPathIcon);
                            String thumbPath = artifactPath + RegistryConstants.PATH_SEPARATOR
                                    + APIConstants.API_ICON_IMAGE;
                            registry.put(thumbPath, resource);
                            GenericArtifact apiArtifact = tenantArtifactManager.getGenericArtifact(apiInfoDTO.getUuid());
                            apiArtifact.setAttribute(APIConstants.API_OVERVIEW_THUMBNAIL_URL, thumbPath);
                            tenantArtifactManager.updateGenericArtifact(apiArtifact);
                        }
                        startTenantFlow(tenant.getDomain(), apiTenantId,
                                MultitenantUtils.getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                        String wsdlResourcePathOld = APIConstants.API_WSDL_RESOURCE_LOCATION
                                + RegistryPersistenceUtil.createWsdlFileName(apiInfoDTO.getApiProvider(),
                                apiInfoDTO.getApiName(), apiInfoDTO.getApiVersion());
                        APIIdentifier identifier = new APIIdentifier(apiInfoDTO.getApiProvider(),
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
                                    + APIConstants.API_WSDL_ARCHIVE_LOCATION + apiInfoDTO.getApiProvider()
                                    + APIConstants.WSDL_PROVIDER_SEPERATOR + apiInfoDTO.getApiName()
                                    + apiInfoDTO.getApiVersion() + APIConstants.ZIP_FILE_EXTENSION;
                            String wsdlResourcePathFile = artifactPath + RegistryConstants.PATH_SEPARATOR
                                    + RegistryPersistenceUtil.createWsdlFileName(apiInfoDTO.getApiProvider(),
                                    apiInfoDTO.getApiName(), apiInfoDTO.getApiVersion());
                            if (APIConstants.APPLICATION_ZIP.equals(resource.getMediaType())) {
                                wsdlResourcePath = wsdlResourcePathArchive;
                            } else {
                                wsdlResourcePath = wsdlResourcePathFile;
                            }
                            registry.copy(resourcePath, wsdlResourcePath);
                            GenericArtifact apiArtifact = tenantArtifactManager.getGenericArtifact(apiInfoDTO.getUuid());
                            String absoluteWSDLResourcePath = RegistryUtils
                                    .getAbsolutePath(RegistryContext.getBaseInstance(),
                                            RegistryConstants.GOVERNANCE_REGISTRY_BASE_PATH) + wsdlResourcePath;
                            String wsdlRegistryPath = RegistryConstants.PATH_SEPARATOR + "registry" +
                                    RegistryConstants.PATH_SEPARATOR + "resource" + absoluteWSDLResourcePath;
                            apiArtifact.setAttribute(APIConstants.API_OVERVIEW_WSDL, wsdlRegistryPath);
                            tenantArtifactManager.updateGenericArtifact(apiArtifact);
                        }
                    }
                }
                PrivilegedCarbonContext.endTenantFlow();
            }
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while initializing the registry", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while Retrieving API artifact from the registry", e);
        }
        log.info("WSO2 API-M Migration Task : Updated Registry paths of API icons and WSDLs of all tenants");
    }

    public void apiRevisionRelatedMigration() throws APIMigrationException {
        boolean isError = false;
        for (Tenant tenant : tenants) {
            try {
                GenericArtifactManager tenantArtifactManager;
                GenericArtifact[] tenantArtifacts = null;
                APIProvider apiProviderTenant = null;
                List<Environment> dynamicEnvironments;
                List<APIInfoDTO> apiInfoDTOList = new ArrayList<>();
                try {
                    log.info("WSO2 API-M Migration Task : Starting API Revision related migration for tenant " +
                            tenant.getId() + '(' + tenant.getDomain() + ')');
                    dynamicEnvironments = ApiMgtDAO.getInstance().getAllEnvironments(tenant.getDomain());
                    APIUtil.loadTenantRegistry(tenant.getId());
                    startTenantFlow(tenant.getDomain(), tenant.getId(), APIUtil.getTenantAdminUserName(tenant.getDomain()));
                    Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().
                            getGovernanceSystemRegistry(tenant.getId());
                    tenantArtifactManager = APIUtil.getArtifactManager(registry, APIConstants.API_KEY);
                    if (tenantArtifactManager != null) {
                        tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                        apiProviderTenant = APIManagerFactory.getInstance().getAPIProvider(
                                APIUtil.getTenantAdminUserName(tenant.getDomain()));
                    }
                    //On exceptions in this stage, log the error with tenant details and continue for remaining tenants
                } catch (GovernanceException e) {
                    log.error("WSO2 API-M Migration Task : Error while initializing the registry " + tenant.getId() +
                            '(' + tenant.getDomain() + ')', e);
                    isError = true;
                    continue;
                } catch (RegistryException e) {
                    log.error("WSO2 API-M Migration Task : Error while initializing the registry " + tenant.getId() +
                            '(' + tenant.getDomain() + ')', e);
                    isError = true;
                    continue;
                } catch (APIManagementException e) {
                    log.error("WSO2 API-M Migration Task : Generic API Management Exception while performing revision " +
                            "migration for " + tenant.getId() + '(' + tenant.getDomain() + ')', e);
                    isError = true;
                    continue;
                }

                for (GenericArtifact artifact : tenantArtifacts) {
                    String identifier = "";
                    try {
                        identifier = artifact.getAttribute(APIConstants.API_OVERVIEW_PROVIDER) + "-" +
                                artifact.getAttribute(APIConstants.API_OVERVIEW_NAME) + "-" +
                                artifact.getAttribute(APIConstants.API_OVERVIEW_VERSION);
                        if (!StringUtils.equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS),
                                org.wso2.carbon.apimgt.impl.APIConstants.CREATED) &&
                                !StringUtils.equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_STATUS),
                                        org.wso2.carbon.apimgt.impl.APIConstants.RETIRED)) {
                            if (!StringUtils.equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE),
                                    APIConstants.API_PRODUCT)) {
                                API api = APIUtil.getAPI(artifact);
                                if (api != null) {
                                    APIInfoDTO apiInfoDTO = new APIInfoDTO();
                                    apiInfoDTO.setUuid(api.getUUID());
                                    apiInfoDTO.setApiProvider(APIUtil.replaceEmailDomainBack(api.getId().
                                            getProviderName()));
                                    apiInfoDTO.setApiName(api.getId().getApiName());
                                    apiInfoDTO.setApiVersion(api.getId().getVersion());
                                    apiInfoDTO.setType(api.getType());
                                    apiInfoDTOList.add(apiInfoDTO);
                                }
                            } else {
                                APIProduct apiProduct = APIUtil.getAPIProduct(artifact, this.registry);
                                if (apiProduct != null) {
                                    APIInfoDTO apiInfoDTO = new APIInfoDTO();
                                    apiInfoDTO.setUuid(apiProduct.getUuid());
                                    apiInfoDTO.setApiProvider(APIUtil.replaceEmailDomainBack(apiProduct.getId().
                                            getProviderName()));
                                    apiInfoDTO.setApiName(apiProduct.getId().getName());
                                    apiInfoDTO.setApiVersion(apiProduct.getId().getVersion());
                                    apiInfoDTO.setType(apiProduct.getType());
                                    apiInfoDTOList.add(apiInfoDTO);
                                }
                            }
                        }
                    } catch (GovernanceException e) {
                        log.error("WSO2 API-M Migration Task : Error while fetching attributes from artifact: " +
                                identifier, e);
                        isError = true;
                    } catch (APIManagementException e) {
                        log.error("WSO2 API-M Migration Task : API Management Exception while getting API/API " +
                                "Product from artifact: " + identifier, e);
                        isError = true;
                    }
                }

                for (APIInfoDTO apiInfoDTO : apiInfoDTOList) {
                    try {
                        //adding the revision
                        APIRevision apiRevision = new APIRevision();
                        apiRevision.setApiUUID(apiInfoDTO.getUuid());
                        apiRevision.setDescription("Initial revision created in migration process");
                        String revisionId;
                        try {
                            if (!StringUtils.equalsIgnoreCase(apiInfoDTO.getType(), APIConstants.API_PRODUCT)) {
                                revisionId = apiProviderTenant.addAPIRevision(apiRevision, tenant.getDomain());
                                log.info("WSO2 API-M Migration Task : Successfully created API Revision for " +
                                        apiInfoDTO.getUuid());
                            } else {
                                revisionId = apiProviderTenant.addAPIProductRevision(apiRevision);
                                log.info("WSO2 API-M Migration Task : Successfully created API Revision for " +
                                        apiInfoDTO.getUuid());
                            }
                        } catch (APIManagementException e) {
                            log.error("WSO2 API-M Migration Task : Error while adding API/API Product Revision for " +
                                    apiInfoDTO.getUuid(), e);
                            isError = true;
                            continue;
                        }
                        // retrieve api artifacts
                        GenericArtifact apiArtifact = tenantArtifactManager.getGenericArtifact(apiInfoDTO.getUuid());
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
                                if (Arrays.stream(arrOfEnvironments).anyMatch(tomlEnv -> StringUtils.equals(tomlEnv, label))) {
                                    // if API is deployed to an environment and label with the same name,
                                    // discard deployment to dynamic environment
                                    continue;
                                }
                                Optional<Environment> optionalEnv = dynamicEnvironments.stream()
                                        .filter(e -> StringUtils.equals(e.getName(), label)).findFirst();
                                Environment dynamicEnv = null;
                                if (optionalEnv != null) {
                                    dynamicEnv = optionalEnv.get();
                                } else {
                                    log.error("WSO2 API-M Migration Task : Error while retrieving dynamic " +
                                            "environment of the label: " + label);
                                    isError = true;
                                    continue;
                                }
                                List<VHost> vhosts = dynamicEnv.getVhosts();
                                if (vhosts.size() > 0) {
                                    APIRevisionDeployment apiRevisionDeployment = new APIRevisionDeployment();
                                    apiRevisionDeployment.setRevisionUUID(revisionId);
                                    apiRevisionDeployment.setDeployment(dynamicEnv.getName());
                                    apiRevisionDeployment.setVhost(vhosts.get(0).getHost());
                                    apiRevisionDeployment.setDisplayOnDevportal(true);
                                    apiRevisionDeployments.add(apiRevisionDeployment);
                                } else {
                                    log.error("WSO2 API-M Migration Task : Vhosts are empty for the dynamic " +
                                            "environment: " + dynamicEnv.getName());
                                    isError = true;
                                }
                            }
                        }

                        if (!apiRevisionDeployments.isEmpty()) {
                            try {
                                if (!StringUtils.equalsIgnoreCase(apiInfoDTO.getType(), APIConstants.API_PRODUCT)) {
                                    apiProviderTenant.deployAPIRevision(apiInfoDTO.getUuid(), revisionId,
                                            apiRevisionDeployments);
                                    log.info("WSO2 API-M Migration Task : Successfully deployed API Revision for " +
                                            apiInfoDTO.getUuid());
                                } else {
                                    apiProviderTenant.deployAPIProductRevision(apiInfoDTO.getUuid(), revisionId,
                                            apiRevisionDeployments);
                                    log.info("WSO2 API-M Migration Task : Successfully deployed API Product Revision" +
                                            " for " + apiInfoDTO.getUuid());
                                }
                            } catch (APIManagementException e) {
                                log.error("WSO2 API-M Migration Task : Error while deploying API/API Product " +
                                        "Revision for " + apiInfoDTO.getUuid(), e);
                                isError = true;
                            }
                        }
                    } catch (GovernanceException e) {
                        log.error("WSO2 API-M Migration Task : Error while fetching registry artifact by UUID: " +
                                apiInfoDTO.getUuid(), e);
                        isError = true;
                    }
                }
            } finally {
                PrivilegedCarbonContext.endTenantFlow();
            }
        }

        if (isError) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Error/s occurred during Revision Related " +
                    "Migration, hence could not create/deploy revisions for some artifacts");
        } else {
            log.info("WSO2 API-M Migration Task : Completed API Revision related migration for all tenants");
        }
    }

    public void migrateProductMappingTable() throws APIMigrationException {
        apiMgtDAO.updateProductMappings();
    }

    protected void startTenantFlow(String tenantDomain, int tenantId, String username) {
        PrivilegedCarbonContext.startTenantFlow();
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain, true);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
        PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(username);
    }

    public void migrateEndpointCertificates() {

        File trustStoreFile = new File(TRUST_STORE);

        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream localTrustStoreStream = new FileInputStream(trustStoreFile)) {
                trustStore.load(localTrustStoreStream, TRUST_STORE_PASSWORD);
            }
            Set<String> aliases = APIMgtDAO.getInstance().retrieveListOfEndpointCertificateAliases();
            Map<String, String> certificateMap = new HashMap<>();
            if (aliases != null) {
                for (String alias : aliases) {
                    Certificate certificate = trustStore.getCertificate(alias);
                    if (certificate != null) {
                        byte[] encoded = Base64.encodeBase64(certificate.getEncoded());
                        String base64EncodedString = BEGIN_CERTIFICATE_STRING.concat(new String(encoded)).concat("\n")
                                .concat(END_CERTIFICATE_STRING);
                        base64EncodedString = Base64.encodeBase64URLSafeString(base64EncodedString.getBytes());
                        certificateMap.put(alias, base64EncodedString);
                        log.info("WSO2 API-M Migration Task : Adding encoded certificate content of alias: " + alias
                                + " to DB");
                    }
                }
            } else {
                log.info("WSO2 API-M Migration Task : No endpoint certificates defined");
            }
            APIMgtDAO.getInstance().updateEndpointCertificates(certificateMap);
        } catch (NoSuchAlgorithmException | IOException | CertificateException
                | KeyStoreException | APIMigrationException e) {
            log.error("WSO2 API-M Migration Task : Error while Migrating Endpoint Certificates", e);
        }
    }

    public void replaceKMNamebyUUID()
            throws APIMigrationException {
        APIMgtDAO apiMgtDAO = APIMgtDAO.getInstance();

        for (Tenant tenant : getTenantsArray()) {
            //Add tenant specific resident key manager with uuids to the AM_KEY_MANAGER table
            log.info("WSO2 API-M Migration Task : Adding default key manager and updating key mappings for tenant: "
                    + tenant.getId() + "(" + tenant.getDomain() + ")");
            addDefaultKM(apiMgtDAO, tenant.getDomain());
            apiMgtDAO.replaceKeyMappingKMNamebyUUID(tenant);
            apiMgtDAO.replaceRegistrationKMNamebyUUID(tenant);
        }
    }

    private void addDefaultKM(APIMgtDAO apiMgtDAO, String tenantDomain) throws APIMigrationException {
        if (apiMgtDAO.getKeyManagerConfigurationByName(tenantDomain,
                org.wso2.carbon.apimgt.impl.APIConstants.KeyManager.DEFAULT_KEY_MANAGER) == null) {

            KeyManagerConfigurationDTO keyManagerConfigurationDTO = new KeyManagerConfigurationDTO();
            keyManagerConfigurationDTO.setName(org.wso2.carbon.apimgt.impl.APIConstants.KeyManager.DEFAULT_KEY_MANAGER);
            keyManagerConfigurationDTO.setEnabled(true);
            keyManagerConfigurationDTO.setUuid(UUID.randomUUID().toString());
            keyManagerConfigurationDTO.setTenantDomain(tenantDomain);
            keyManagerConfigurationDTO.setDescription(
                    org.wso2.carbon.apimgt.impl.APIConstants.KeyManager.DEFAULT_KEY_MANAGER_DESCRIPTION);
            keyManagerConfigurationDTO
                    .setType(org.wso2.carbon.apimgt.impl.APIConstants.KeyManager.DEFAULT_KEY_MANAGER_TYPE);
            TokenHandlingDTO tokenHandlingDto = new TokenHandlingDTO();
            tokenHandlingDto.setEnable(true);
            tokenHandlingDto.setType(TokenHandlingDTO.TypeEnum.REFERENCE);
            tokenHandlingDto.setValue(org.wso2.carbon.apimgt.impl.APIConstants.KeyManager.UUID_REGEX);
            keyManagerConfigurationDTO
                    .addProperty(org.wso2.carbon.apimgt.impl.APIConstants.KeyManager.TOKEN_FORMAT_STRING,
                            new Gson().toJson(Arrays.asList(tokenHandlingDto)));
            apiMgtDAO.addKeyManagerConfiguration(keyManagerConfigurationDTO);
        }
    }
  
    public void migrateLabelsToVhosts() {
        try {
            // retrieve labels
            List<LabelDTO> labelDTOS = apiMgtDAO.getLabels();
            List<GatewayEnvironmentDTO> environments = new ArrayList<>(labelDTOS.size());

            // converts to dynamic environments
            for (LabelDTO labelDTO : labelDTOS) {
                GatewayEnvironmentDTO environment = new GatewayEnvironmentDTO();
                environment.setUuid(labelDTO.getLabelId());
                // skip checking an environment exists with the same name in deployment toml
                // eg: label 'Default' and environment 'Default' in toml.
                environment.setName(labelDTO.getName());
                environment.setDisplayName(labelDTO.getName());
                environment.setDescription(labelDTO.getDescription());
                environment.setTenantDomain(labelDTO.getTenantDomain());

                List<VHost> vhosts = new ArrayList<>(labelDTO.getAccessUrls().size());
                for (String accessUrl : labelDTO.getAccessUrls()) {
                    if (!StringUtils.contains(accessUrl, VHost.PROTOCOL_SEPARATOR)) {
                        accessUrl = VHost.HTTPS_PROTOCOL + VHost.PROTOCOL_SEPARATOR + accessUrl;
                    }
                    VHost vhost = VHost.fromEndpointUrls(new String[]{accessUrl});
                    vhosts.add(vhost);
                }
                environment.setVhosts(vhosts);
                environments.add(environment);
                log.info("WSO2 API-M Migration Task : Converting label " + labelDTO.getName() + "of tenant " +
                        labelDTO.getTenantDomain() + " to a Vhost");
            }
            // insert dynamic environments
            apiMgtDAO.addDynamicGatewayEnvironments(environments);
            if (!labelDTOS.isEmpty()) {
                log.info("WSO2 API-M Migration Task : Converted labels to Vhosts");
            } else {
                log.info("WSO2 API-M Migration Task : No labels found");
            }
            apiMgtDAO.dropLabelTable();
            log.info("WSO2 API-M Migration Task : Dropped AM_LABELS and AM_LABEL_URLS tables");
        } catch (APIMigrationException e) {
            log.error("WSO2 API-M Migration Task : Error while Reading Labels", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while Converting Endpoint URLs to VHost", e);
        }
    }

    public void migrateWebSocketAPI() {
        try {
            // migrate registry artifacts
            List<Integer> wsAPIs = new ArrayList<>();
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            Map<String, String> wsUriMapping = new HashMap<>();
            for (Tenant tenant : tenants) {
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);
                startTenantFlow(tenant.getDomain(), apiTenantId,
                        MultitenantUtils.getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                this.registry = ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceSystemRegistry(apiTenantId);
                GenericArtifactManager tenantArtifactManager = APIUtil.getArtifactManager(this.registry,
                        APIConstants.API_KEY);
                if (tenantArtifactManager != null) {
                    GenericArtifact[] tenantArtifacts = tenantArtifactManager.getAllGenericArtifacts();
                    for (GenericArtifact artifact : tenantArtifacts) {
                        if (StringUtils.equalsIgnoreCase(artifact.getAttribute(APIConstants.API_OVERVIEW_TYPE),
                                APIConstants.APITransportType.WS.toString())) {
                            int id = Integer.parseInt(APIMgtDAO.getInstance().getAPIID(
                                    artifact.getAttribute(Constants.API_OVERVIEW_CONTEXT)));
                            wsAPIs.add(id);
                            artifact.setAttribute(APIConstants.API_OVERVIEW_WS_URI_MAPPING,
                                    new Gson().toJson(wsUriMapping));
                            tenantArtifactManager.updateGenericArtifact(artifact);

                            API api = APIUtil.getAPI(artifact);
                            if (api != null) {

                                AsyncApiParser asyncApiParser = new AsyncApiParser();
                                String apiDefinition = asyncApiParser.generateAsyncAPIDefinition(api);
                                APIProvider apiProviderTenant = APIManagerFactory.getInstance().getAPIProvider(
                                        APIUtil.getTenantAdminUserName(tenant.getDomain()));
                                apiProviderTenant.saveAsyncApiDefinition(api, apiDefinition);
                            } else {
                                log.error("WSO2 API-M Migration Task : Async Api definition is not added for the API " +
                                        artifact.getAttribute(org.wso2.carbon.apimgt.impl.APIConstants.API_OVERVIEW_NAME)
                                        + " due to returned API is null");
                            }
                        }
                    }
                }
                PrivilegedCarbonContext.endTenantFlow();
            }
            // Remove previous entries(In 3.x we are setting default REST methods with /*)
            apiMgtDAO.removePreviousURLTemplatesForWSAPIs(wsAPIs);
            //  add default url templates
            apiMgtDAO.addDefaultURLTemplatesForWSAPIs(wsAPIs);
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while initializing the registry", e);
        } catch (UserStoreException e) {
            log.error("WSO2 API-M Migration Task : Error while retrieving the tenants", e);
        } catch (APIManagementException e) {
            log.error("WSO2 API-M Migration Task : Error while Retrieving API artifact from the registry", e);
        } catch (APIMigrationException e) {
            log.error("WSO2 API-M Migration Task : Error while migrating WebSocket APIs", e);
        }
    }

    public void removeUnnecessaryFaultHandlers() {
        try {
            List<Tenant> tenants = APIUtil.getAllTenantsWithSuperTenant();
            for (Tenant tenant : tenants) {
                int apiTenantId = tenantManager.getTenantId(tenant.getDomain());
                APIUtil.loadTenantRegistry(apiTenantId);
                startTenantFlow(tenant.getDomain(), apiTenantId,
                        MultitenantUtils.getTenantAwareUsername(APIUtil.getTenantAdminUserName(tenant.getDomain())));
                this.registry = ServiceReferenceHolder.getInstance().getRegistryService()
                        .getGovernanceSystemRegistry(apiTenantId);
                // Fault Handlers that needs to be removed from fault sequences
                String unnecessaryFaultHandler1 = "org.wso2.carbon.apimgt.usage.publisher.APIMgtFaultHandler";
                String unnecessaryFaultHandler2 = "org.wso2.carbon.apimgt.gateway.handlers.analytics.APIMgtFaultHandler";
                org.wso2.carbon.registry.api.Collection seqCollection = null;
                String resourcePath = org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_LOCATION
                        + RegistryConstants.PATH_SEPARATOR
                        + org.wso2.carbon.apimgt.impl.APIConstants.API_CUSTOM_SEQUENCE_TYPE_FAULT;
                if (registry.resourceExists(resourcePath)) {
                    seqCollection = (org.wso2.carbon.registry.api.Collection) registry
                            .get(resourcePath);
                } else {
                    log.warn("WSO2 API-M Migration Task : No fault sequences found for tenant: " + tenant.getDomain());
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
                            if (unnecessaryFaultHandler1.equals(namedItem.getNodeValue()) || unnecessaryFaultHandler2
                                    .equals(namedItem.getNodeValue())) {
                                Node parentNode = node.getParentNode();
                                parentNode.removeChild(node);
                                parentNode.normalize();
                            }
                        }
                        // Convert the content to String
                        String newContent = toString(doc);
                        // Update the registry with the new content
                        sequence.setContent(newContent);
                        registry.put(childPath, sequence);
                    }
                }
                PrivilegedCarbonContext.endTenantFlow();
            }
        } catch (RegistryException e) {
            log.error("WSO2 API-M Migration Task : Error while initiating the registry", e);
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

    private static String toString(Document newDoc) throws Exception {
        DOMSource domSource = new DOMSource(newDoc);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        StringWriter sw = new StringWriter();
        StreamResult sr = new StreamResult(sw);
        transformer.transform(domSource, sr);
        String output = sw.toString();
        return output.substring(output.indexOf("?>") + 2);
    }
}
