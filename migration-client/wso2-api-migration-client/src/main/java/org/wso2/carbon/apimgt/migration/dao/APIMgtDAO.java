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

package org.wso2.carbon.apimgt.migration.dao;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.APIRevision;
import org.wso2.carbon.apimgt.api.model.VHost;
import org.wso2.carbon.apimgt.impl.dao.constants.SQLConstants;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.api.model.Scope;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.dto.GatewayEnvironmentDTO;
import org.wso2.carbon.apimgt.migration.dto.LabelDTO;
import org.wso2.carbon.apimgt.migration.dto.ResourceScopeInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.ScopeInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.APIURLMappingInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.APIInfoDTO;
import org.wso2.carbon.apimgt.migration.dto.APIInfoScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.dto.APIScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.dto.AMAPIResourceScopeMappingDTO;
import org.wso2.carbon.apimgt.migration.dto.KeyManagerConfigurationDTO;
import org.wso2.carbon.apimgt.migration.util.Constants;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.utils.DBUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

public class APIMgtDAO {

    private static final Log log = LogFactory.getLog(APIMgtDAO.class);
    private static APIMgtDAO INSTANCE = null;
    private static final String RESOURCE_PATH = "RESOURCE_PATH";
    private static final String SCOPE_NAME = "NAME";
    private static final String URL_MAPPING_ID = "URL_MAPPING_ID";
    private static final String URL_PATTERN = "URL_PATTERN";
    private static final String HTTP_METHOD = "HTTP_METHOD";
    private static final String API_ID = "API_ID";
    private static final String API_NAME = "API_NAME";
    private static final String API_VERSION = "API_VERSION";
    private static final String API_PROVIDER = "API_PROVIDER";
    private static final String CONTEXT = "CONTEXT";
    private static final String CONTEXT_TEMPLATE = "CONTEXT_TEMPLATE";
    private static final String SCOPE_ID = "SCOPE_ID";
    private static final String SCOPE_DISPLAY_NAME = "DISPLAY_NAME";
    private static final String SCOPE_DESCRIPTION = "DESCRIPTION";
    private static final String SCOPE_TYPE = "SCOPE_TYPE";
    private static final String TENANT_ID = "TENANT_ID";
    private static String GET_RESOURCE_SCOPE_SQL = "SELECT * FROM IDN_OAUTH2_RESOURCE_SCOPE";
    private static String GET_AM_API_SQL = "SELECT * FROM AM_API";
    private static String GET_AM_API_URL_MAPPING_SQL = "SELECT * FROM AM_API_URL_MAPPING";
    private static String GET_API_INFO_SCOPE_SQL = "SELECT APIS.API_ID, APIS.SCOPE_ID, API.API_NAME, " +
            " API.API_VERSION, API.API_PROVIDER, IOS.NAME, IORS.RESOURCE_PATH FROM " +
            " ((AM_API_SCOPES APIS LEFT JOIN IDN_OAUTH2_SCOPE IOS  ON APIS.SCOPE_ID =  IOS.SCOPE_ID ) " +
            " LEFT JOIN IDN_OAUTH2_RESOURCE_SCOPE IORS ON APIS.SCOPE_ID =  IORS.SCOPE_ID) LEFT JOIN AM_API API " +
            " ON API.API_ID = APIS.API_ID";
    private static String GET_AM_API_SCOPE_SQL = "SELECT * FROM AM_API_SCOPES";
    private static String GET_SCOPE_BY_ID_SQL = "SELECT * FROM IDN_OAUTH2_SCOPE WHERE SCOPE_ID = ?";
    private static String INSERT_INTO_AM_API_RESOURCE_SCOPE_MAPPING =
            "INSERT INTO AM_API_RESOURCE_SCOPE_MAPPING (SCOPE_NAME, URL_MAPPING_ID, TENANT_ID) VALUES " +
                    "(?,?,?)";
    private static String GET_API_ID = "SELECT API_ID FROM AM_API WHERE CONTEXT = ?";
    private static String GET_SCOPE_ID = "SELECT SCOPE_ID FROM IDN_OAUTH2_RESOURCE_SCOPE WHERE RESOURCE_PATH = ?";

    private static String DELETE_SCOPE_FROM_AM_API_SCOPES = "DELETE FROM AM_API_SCOPES WHERE" +
            " API_ID = ? AND SCOPE_ID = ?";
    private static String DELETE_SCOPE_FROM_IDN_OAUTH2_SCOPES = "DELETE FROM IDN_OAUTH2_SCOPE WHERE" +
            " SCOPE_ID = ?";
    private static String UPDATE_SCOPE_ID_IN_RESOURCE = "UPDATE" +
            " IDN_OAUTH2_RESOURCE_SCOPE SET SCOPE_ID = ? WHERE SCOPE_ID = ?";
    private static String GET_APPS_OF_TYPE_JWT = "SELECT IOP.CONSUMER_KEY FROM AM_APPLICATION AMA " +
            "INNER JOIN AM_APPLICATION_KEY_MAPPING AKM ON AMA.APPLICATION_ID=AKM.APPLICATION_ID " +
            "INNER JOIN IDN_OIDC_PROPERTY IOP ON AKM.CONSUMER_KEY=IOP.CONSUMER_KEY " +
            "WHERE AMA.TOKEN_TYPE = 'JWT' AND PROPERTY_KEY = 'tokenType' AND TENANT_ID = ?";
    private static String UPDATE_TOKEN_TYPE_TO_JWT = "UPDATE IDN_OIDC_PROPERTY SET" +
            " PROPERTY_VALUE = ? WHERE PROPERTY_KEY  = 'tokenType' AND CONSUMER_KEY = ?";
    private static String UPDATE_UUID_BY_THE_IDENTIFIER = "UPDATE AM_API SET API_UUID = ?,STATUS = ? WHERE " +
            "API_PROVIDER = ? AND API_NAME = ? AND API_VERSION = ? ";
    private static String INSERT_URL_MAPPINGS_FOR_WS_APIS =
            "INSERT INTO AM_API_URL_MAPPING (API_ID,HTTP_METHOD,AUTH_SCHEME,URL_PATTERN) VALUES (?,?,?,?)";
    private static String REMOVE_URL_MAPPINGS_FOR_WS_APIS = "DELETE FROM AM_API_URL_MAPPING WHERE API_ID = ?";
    private static String GET_ALL_API_IDENTIFIERS = "SELECT API_PROVIDER, API_NAME, API_VERSION FROM AM_API";

    private static final String RETRIEVE_ENDPOINT_CERTIFICATE_ALIASES = "SELECT ALIAS FROM AM_CERTIFICATE_METADATA";
    private static final String UPDATE_ENDPOINT_CERTIFICATES = "UPDATE AM_CERTIFICATE_METADATA SET CERTIFICATE = ? " +
            "WHERE ALIAS = ?";
    private static String UPDATE_REVISION_UUID_PRODUCT_MAPPINGS = "UPDATE AM_API_PRODUCT_MAPPING SET REVISION_UUID =" +
            " 'Current API'";
    private static String GET_LABEL_SQL = "SELECT AM_LABELS.LABEL_ID, AM_LABELS.NAME, AM_LABELS.DESCRIPTION, " +
            "AM_LABELS.TENANT_DOMAIN, AM_LABEL_URLS.ACCESS_URL " +
            "FROM AM_LABELS LEFT JOIN AM_LABEL_URLS ON AM_LABELS.LABEL_ID=AM_LABEL_URLS.LABEL_ID";
    private static String INSERT_GATEWAY_ENVIRONMENT = "INSERT INTO AM_GATEWAY_ENVIRONMENT " +
            "(UUID, NAME, TENANT_DOMAIN, DISPLAY_NAME, DESCRIPTION) VALUES (?,?,?,?,?)";
    private static String INSERT_VHOST = "INSERT INTO AM_GW_VHOST " +
            "(GATEWAY_ENV_ID, HOST, HTTP_CONTEXT, HTTP_PORT, HTTPS_PORT, WS_PORT, WSS_PORT) VALUES (?,?,?,?,?,?,?)";
    private static String DROP_AM_LABELS_TABLE = "DROP TABLE AM_LABELS";
    private static String DROP_AM_LABEL_URLS_TABLE = "DROP TABLE AM_LABEL_URLS";
    public static String GET_CURRENT_API_PRODUCT_RESOURCES = "SELECT URL_MAPPING_ID, API_ID FROM AM_API_PRODUCT_MAPPING";

    public static String GET_URL_MAPPINGS_WITH_SCOPE_BY_URL_MAPPING_ID = "SELECT AUM.HTTP_METHOD, AUM.AUTH_SCHEME, " +
            "AUM.URL_PATTERN, AUM.THROTTLING_TIER, AUM.MEDIATION_SCRIPT, ARSM.SCOPE_NAME, AUM.API_ID " +
            "FROM AM_API_URL_MAPPING AUM LEFT JOIN AM_API_RESOURCE_SCOPE_MAPPING ARSM ON AUM.URL_MAPPING_ID = ARSM.URL_MAPPING_ID " +
            "WHERE AUM.URL_MAPPING_ID = ?";

    public static String INSERT_URL_MAPPINGS = "INSERT INTO AM_API_URL_MAPPING(API_ID, HTTP_METHOD," +
            " AUTH_SCHEME, URL_PATTERN, THROTTLING_TIER, REVISION_UUID) VALUES(?,?,?,?,?,?)";

    public static String GET_URL_MAPPINGS_ID = "SELECT URL_MAPPING_ID FROM AM_API_URL_MAPPING " +
            "WHERE API_ID = ? AND HTTP_METHOD = ? AND AUTH_SCHEME = ? AND URL_PATTERN = ? " +
            "AND THROTTLING_TIER = ? AND REVISION_UUID = ?";

    public static String INSERT_SCOPE_RESOURCE_MAPPING = "INSERT INTO AM_API_RESOURCE_SCOPE_MAPPING" +
            "(SCOPE_NAME, URL_MAPPING_ID, TENANT_ID) VALUES (?, ?, ?)";

    public static String ADD_PRODUCT_RESOURCE_MAPPING_SQL = "INSERT INTO AM_API_PRODUCT_MAPPING "
            + "(API_ID,URL_MAPPING_ID,REVISION_UUID) " + "VALUES (?, ?, ?)";

    public static final String REMOVE_PRODUCT_ENTRIES_IN_AM_API_PRODUCT_MAPPING =
            "DELETE FROM AM_API_PRODUCT_MAPPING WHERE URL_MAPPING_ID = ? AND API_ID = ?";

    private static String GET_KEY_MAPPING =
            "SELECT AM_APPLICATION_KEY_MAPPING.UUID AS KEY_ID, AM_KEY_MANAGER.UUID AS" +
                    " KEY_MANAGER_ID " +
                    "FROM " +
                    "AM_APPLICATION_KEY_MAPPING " +
                    "INNER JOIN AM_APPLICATION ON AM_APPLICATION_KEY_MAPPING.APPLICATION_ID=AM_APPLICATION.APPLICATION_ID " +
                    "INNER JOIN AM_SUBSCRIBER ON AM_SUBSCRIBER.SUBSCRIBER_ID=AM_APPLICATION.SUBSCRIBER_ID " +
                    "INNER JOIN AM_KEY_MANAGER ON AM_KEY_MANAGER.NAME=AM_APPLICATION_KEY_MAPPING.KEY_MANAGER " +
                    "WHERE " +
                    "AM_SUBSCRIBER.TENANT_ID = ? AND " +
                    "AM_KEY_MANAGER.TENANT_DOMAIN = ?";

    private static String GET_APP_REG =
            "SELECT AM_APPLICATION_REGISTRATION.REG_ID AS REG_ID, AM_KEY_MANAGER.UUID AS" +
                    " KEY_MANAGER_ID " +
                    "FROM " +
                    "AM_APPLICATION_REGISTRATION " +
                    "INNER JOIN AM_APPLICATION ON AM_APPLICATION_REGISTRATION.APP_ID=AM_APPLICATION.APPLICATION_ID " +
                    "INNER JOIN AM_SUBSCRIBER ON AM_SUBSCRIBER.SUBSCRIBER_ID=AM_APPLICATION.SUBSCRIBER_ID " +
                    "INNER JOIN AM_KEY_MANAGER ON AM_KEY_MANAGER.NAME=AM_APPLICATION_REGISTRATION.KEY_MANAGER " +
                    "WHERE " +
                    "AM_SUBSCRIBER.TENANT_ID = ? AND " +
                    "AM_KEY_MANAGER.TENANT_DOMAIN = ?";

    private static String UPDATE_KEY_MAPPINGS =
            "UPDATE AM_APPLICATION_KEY_MAPPING " +
                    "SET KEY_MANAGER = ? " +
                    "WHERE " +
                    "AM_APPLICATION_KEY_MAPPING.UUID = ?";

    private static String UPDATE_APP_REG =
            "UPDATE AM_APPLICATION_REGISTRATION " +
                    "SET KEY_MANAGER = ? " +
                    "WHERE " +
                    "AM_APPLICATION_REGISTRATION.REG_ID = ?";

    private static String GET_API_ID_OF_WS_APIS = "SELECT API_ID FROM AM_API WHERE API_TYPE = 'WS'";
    private static final String ADD_KEY_MANAGER =
            " INSERT INTO AM_KEY_MANAGER (UUID,NAME,DESCRIPTION,TYPE,CONFIGURATION,TENANT_DOMAIN,ENABLED," +
                    "DISPLAY_NAME) VALUES (?,?,?,?,?,?,?,?)";

    private static final String GET_REVISION_APIID_BY_REVISION_UUID = "SELECT API_UUID, ID FROM AM_REVISION WHERE "
            + "REVISION_UUID = ?";

    private APIMgtDAO() {

    }

    public static APIMgtDAO getInstance() {

        if (INSTANCE == null) {
            INSTANCE = new APIMgtDAO();
        }
        return INSTANCE;
    }

    /**
     * This mehthod is used to get data from IDN_OAUTH2_RESOURCE_SCOPE by tenant id
     *
     * @return
     * @throws APIMigrationException
     */
    public ArrayList<ResourceScopeInfoDTO> getResourceScopeData() throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_RESOURCE_SCOPE_SQL)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    ArrayList<ResourceScopeInfoDTO> scopeInfoList = new ArrayList<>();
                    while (resultSet.next()) {
                        ResourceScopeInfoDTO scopeInfo = new ResourceScopeInfoDTO();
                        scopeInfo.setResourcePath(resultSet.getString(RESOURCE_PATH));
                        scopeInfo.setScopeId(resultSet.getString(SCOPE_ID));
                        scopeInfo.setTenantID(resultSet.getString(TENANT_ID));
                        scopeInfoList.add(scopeInfo);
                    }
                    return scopeInfoList;
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from IDN_OAUTH2_RESOURCE_SCOPE", ex);
        }
    }

    /**
     * This mehthod is used to get data from AM_API table
     *
     * @return
     * @throws APIMigrationException
     */
    public ArrayList<APIInfoDTO> getAPIData() throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_AM_API_SQL)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    ArrayList<APIInfoDTO> apiInfoList = new ArrayList<>();
                    while (resultSet.next()) {
                        APIInfoDTO apiInfoDTO = new APIInfoDTO();
                        apiInfoDTO.setApiId(resultSet.getInt(API_ID));
                        apiInfoDTO.setApiName(resultSet.getString(API_NAME));
                        apiInfoDTO.setApiProvider(resultSet.getString(API_PROVIDER));
                        apiInfoDTO.setApiVersion(resultSet.getString(API_VERSION));
                        apiInfoDTO.setApiContext(resultSet.getString(CONTEXT));
                        apiInfoDTO.setGetApiContextTemplate(resultSet.getString(CONTEXT_TEMPLATE));
                        apiInfoList.add(apiInfoDTO);
                    }
                    return apiInfoList;
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from AM_API table", ex);
        }
    }

    /**
     * This method is used to get data from AM_API_URL_MAPPING table
     *
     * @return
     * @throws APIMigrationException
     */
    public ArrayList<APIURLMappingInfoDTO> getAPIURLMappingData() throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_AM_API_URL_MAPPING_SQL)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    ArrayList<APIURLMappingInfoDTO> apiurlMappingInfoDTOArrayList = new ArrayList<>();
                    while (resultSet.next()) {
                        APIURLMappingInfoDTO apiurlMappingInfoDTO = new APIURLMappingInfoDTO();
                        apiurlMappingInfoDTO.setUrlMappingId(resultSet.getInt(URL_MAPPING_ID));
                        apiurlMappingInfoDTO.setApiId(resultSet.getInt(API_ID));
                        apiurlMappingInfoDTO.setHttpMethod(resultSet.getString(HTTP_METHOD));
                        apiurlMappingInfoDTO.setUrlPattern(resultSet.getString(URL_PATTERN));
                        apiurlMappingInfoDTOArrayList.add(apiurlMappingInfoDTO);
                    }
                    return apiurlMappingInfoDTOArrayList;
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from AM_API_URL_MAPPING table", ex);
        }
    }

    /**
     * This method is used to get data from AM_API_SCOPE, IDN_OAUTH2_SCOPE by tenant id
     *
     * @return
     * @throws APIMigrationException
     */
    public ArrayList<APIInfoScopeMappingDTO> getAPIInfoScopeData() throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_API_INFO_SCOPE_SQL)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    ArrayList<APIInfoScopeMappingDTO> scopeAPIInfoList = new ArrayList<>();
                    while (resultSet.next()) {
                        APIInfoScopeMappingDTO apiInfoScopeMappingDTO = new APIInfoScopeMappingDTO();
                        apiInfoScopeMappingDTO.setScopeId(resultSet.getInt(SCOPE_ID));
                        apiInfoScopeMappingDTO.setApiId(resultSet.getInt(API_ID));
                        apiInfoScopeMappingDTO.setScopeName(resultSet.getString(SCOPE_NAME));
                        apiInfoScopeMappingDTO.setApiName(resultSet.getString(API_NAME));
                        apiInfoScopeMappingDTO.setApiProvider(resultSet.getString(API_PROVIDER));
                        apiInfoScopeMappingDTO.setApiVersion(resultSet.getString(API_VERSION));
                        apiInfoScopeMappingDTO.setResourcePath(resultSet.getString(RESOURCE_PATH));
                        scopeAPIInfoList.add(apiInfoScopeMappingDTO);
                    }
                    return scopeAPIInfoList;
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from AM_API_SCOPE, IDN_OAUTH2_SCOPE tables", ex);
        }
    }

    /**
     * Alter the scope id in the resource scope
     *
     * @param scopeId      Scope ID
     * @param resourcePath Resource Path
     * @param newScopeId   Scope ID
     */
    public static void updateScopeResource(int newScopeId, String resourcePath, int scopeId) throws APIMigrationException {

        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_SCOPE_ID_IN_RESOURCE)) {
                preparedStatement.setInt(1, newScopeId);
                preparedStatement.setInt(2, scopeId);
                preparedStatement.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_SCOPE_ID_IN_RESOURCE), e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_SCOPE_ID_IN_RESOURCE), e);
        }
    }

    /**
     * This mehthod is used to get data from AM_API_SCOPE
     *
     * @return
     * @throws APIMigrationException
     */
    public ArrayList<APIScopeMappingDTO> getAMScopeData() throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_AM_API_SCOPE_SQL)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    ArrayList<APIScopeMappingDTO> scopeInfoList = new ArrayList<>();
                    while (resultSet.next()) {
                        APIScopeMappingDTO apiScopeMappingDTO = new APIScopeMappingDTO();
                        apiScopeMappingDTO.setApiId(resultSet.getInt(API_ID));
                        apiScopeMappingDTO.setScopeId(resultSet.getInt(SCOPE_ID));
                        scopeInfoList.add(apiScopeMappingDTO);
                    }
                    return scopeInfoList;
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from AM_API_SCOPE", ex);
        }
    }

    /**
     * This method is used to get API Id using API context
     *
     * @param context
     * @return
     * @throws APIMigrationException
     */
    public String getAPIID(String context) throws APIMigrationException {

        String apiId = null;
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_API_ID)) {
                ps.setString(1, context);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        apiId = rs.getString("API_ID");
                    }
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get API ID using context : " + context, ex);
        }
        return apiId;
    }

    /**
     * This method is used to retrieve the scope id given the resource path
     *
     * @param resourcePath
     * @throws APIMigrationException
     */
    public int getScopeId(String resourcePath)
            throws APIMigrationException {

        int scopeId = -1;
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_SCOPE_ID)) {
                ps.setString(1, resourcePath);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        scopeId = rs.getInt(SCOPE_ID);
                    }
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get SCOPE ID using resource path : " + resourcePath, ex);
        }
        return scopeId;
    }

    /**
     * This method is used to retrieve the scope details given the scopeId
     *
     * @param scopeId
     * @throws APIMigrationException
     */
    public ScopeInfoDTO getScopeInfoByScopeId(int scopeId)
            throws APIMigrationException {

        ScopeInfoDTO scopeInfoDTO = new ScopeInfoDTO();
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_SCOPE_BY_ID_SQL)) {
                ps.setInt(1, scopeId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        scopeInfoDTO.setScopeId(rs.getInt(SCOPE_ID));
                        scopeInfoDTO.setScopeName(rs.getString(SCOPE_NAME));
                        scopeInfoDTO.setScopeDisplayName(rs.getString(SCOPE_DISPLAY_NAME));
                        scopeInfoDTO.setScopeDescription(rs.getString(SCOPE_DESCRIPTION));
                        scopeInfoDTO.setTenantID(rs.getInt(TENANT_ID));
                    }
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to retrieve the scope details given the scopeId : " + scopeId, ex);
        }
        return scopeInfoDTO;
    }

    /**
     * This method is used to insert data to AM_API_RESOURCE_SCOPE_MAPPING table
     *
     * @param resourceScopeMappingDTOS
     * @throws APIMigrationException
     */
    public void addDataToResourceScopeMapping(List<AMAPIResourceScopeMappingDTO> resourceScopeMappingDTOS)
            throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement psAddResourceScope =
                         conn.prepareStatement(INSERT_INTO_AM_API_RESOURCE_SCOPE_MAPPING)) {
                for (AMAPIResourceScopeMappingDTO resourceScopeMappingDTO : resourceScopeMappingDTOS) {
                    psAddResourceScope.setString(1, resourceScopeMappingDTO.getScopeName());
                    psAddResourceScope.setInt(2, resourceScopeMappingDTO.getUrlMappingId());
                    psAddResourceScope.setInt(3, resourceScopeMappingDTO.getTenantId());
                    psAddResourceScope.addBatch();
                }
                psAddResourceScope.executeBatch();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw new APIMigrationException("Failed to add data to AM_API_RESOURCE_SCOPE_MAPPING table : ", ex);
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to add data to AM_API_RESOURCE_SCOPE_MAPPING table : ", ex);
        }
    }

    /**
     * This method is used to remove the duplicate data from IDN_OAUTH2_SCOPE, AM_API_SCOPE
     * and IDN_OAUTH2_SCOPE_BINDING tables
     *
     * @param duplicateList
     * @throws APIMigrationException
     */
    public void removeDuplicateScopeEntries(ArrayList<APIScopeMappingDTO> duplicateList)
            throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            for (APIScopeMappingDTO apiScopeMappingDTOS : duplicateList) {
                try (PreparedStatement preparedStatement = conn.prepareStatement(DELETE_SCOPE_FROM_AM_API_SCOPES)) {
                    preparedStatement.setInt(1, apiScopeMappingDTOS.getApiId());
                    preparedStatement.setInt(2, apiScopeMappingDTOS.getScopeId());
                    preparedStatement.executeUpdate();
                }
                try (PreparedStatement preparedStatement = conn.prepareStatement(DELETE_SCOPE_FROM_IDN_OAUTH2_SCOPES)) {
                    preparedStatement.setInt(1, apiScopeMappingDTOS.getScopeId());
                    preparedStatement.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to delete duplicate scope data : ", ex);
        }
    }

    /**
     * Get the list of consumer keys corresponding to the apps created of the type JWT
     *
     * @param tenantId Relevant tenant ID of the service provider owner
     * @return List of consumer keys corresponding to the apps created of the type JWT
     */
    public static ArrayList<String> getAppsOfTypeJWT(int tenantId) throws APIMigrationException {

        ArrayList<String> consumerKeys = new ArrayList<String>();
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(GET_APPS_OF_TYPE_JWT)) {
                preparedStatement.setInt(1, tenantId);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    connection.commit();
                    while (resultSet.next()) {
                        consumerKeys.add(resultSet.getString("CONSUMER_KEY"));
                    }
                }
            }

        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(GET_APPS_OF_TYPE_JWT), e);
        }
        return consumerKeys;
    }

    /**
     * Updates the token type of the service provider corresponding to the consumer key provided to the JWT token type
     *
     * @param consumerKey The consumer key of the application that needs to be altered
     */
    public static void updateTokenTypeToJWT(String consumerKey) throws APIMigrationException {

        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_TOKEN_TYPE_TO_JWT)) {
                preparedStatement.setString(1, "JWT");
                preparedStatement.setString(2, consumerKey);
                preparedStatement.executeUpdate();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_TOKEN_TYPE_TO_JWT), e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_TOKEN_TYPE_TO_JWT), e);
        }
    }

    /**
     * This method is used to fetch and update the key mapping table with the key manager ID
     *
     * @throws APIMigrationException
     */
    public void replaceKeyMappingKMNamebyUUID(Tenant tenant) throws APIMigrationException {
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            HashMap<String, String> results = new HashMap<>();
            try {
                try (PreparedStatement preparedStatement = connection.prepareStatement(GET_KEY_MAPPING)) {
                    preparedStatement.setInt(1, tenant.getId());
                    preparedStatement.setString(2, tenant.getDomain());
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            String keyID = resultSet.getString("KEY_ID");
                            String keyManagerUUID = resultSet.getString("KEY_MANAGER_ID");
                            results.put(keyID, keyManagerUUID);
                        }
                    }
                }
                updateKeyMappings(connection, results);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new APIMigrationException("SQLException when executing: ".concat(GET_KEY_MAPPING), e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(GET_KEY_MAPPING), e);
        }
    }

    /**
     * This method is used to update the key mapping table with the key manager ID
     *
     * @throws APIMigrationException
     */
    public void updateKeyMappings(Connection connection, HashMap<String, String> keyMappingEntries)
            throws APIMigrationException {
        try {
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_KEY_MAPPINGS)) {
                for (String key : keyMappingEntries.keySet()) {
                    preparedStatement.setString(1, keyMappingEntries.get(key));
                    preparedStatement.setString(2, key);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_KEY_MAPPINGS), e);
        }
    }


    /**
     * This method is used to fetch and update the app registration table with the key manager ID
     *
     * @throws APIMigrationException
     */
    public void replaceRegistrationKMNamebyUUID(Tenant tenant) throws APIMigrationException {
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try {
                HashMap<Integer, String> results = new HashMap<>();
                connection.setAutoCommit(false);
                try (PreparedStatement preparedStatement = connection.prepareStatement(GET_APP_REG)) {
                    preparedStatement.setInt(1, tenant.getId());
                    preparedStatement.setString(2, tenant.getDomain());
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            Integer regID = resultSet.getInt("REG_ID");
                            String keyManagerUUID = resultSet.getString("KEY_MANAGER_ID");
                            results.put(regID, keyManagerUUID);
                        }
                    }
                }
                updateAppRegistration(connection, results);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new APIMigrationException("SQLException when executing: ".concat(GET_APP_REG), e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(GET_APP_REG), e);
        }
    }

    /**
     * This method is used to update the app registration table with the key manager ID
     *
     * @throws APIMigrationException
     */
    public void updateAppRegistration(Connection connection, HashMap<Integer, String> registrationEntries)
            throws APIMigrationException {
        try {
            if (registrationEntries == null || registrationEntries.size() == 0) {
                return;
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_APP_REG)) {
                for (Integer key : registrationEntries.keySet()) {
                    preparedStatement.setString(1, registrationEntries.get(key));
                    preparedStatement.setInt(2, key);
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_APP_REG), e);
        }
    }

    /**
     * This method is used to set the UUID in the DB using Api details
     *
     * @param apiInfoDTOS API Information list
     * @throws APIMigrationException
     */
    public void updateUUIDAndStatus(List<APIInfoDTO> apiInfoDTOS) throws APIMigrationException {

        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_UUID_BY_THE_IDENTIFIER)) {
                for (APIInfoDTO apiInfoDTO : apiInfoDTOS) {
                    preparedStatement.setString(1, apiInfoDTO.getUuid());
                    preparedStatement.setString(2, apiInfoDTO.getStatus());
                    preparedStatement.setString(3, apiInfoDTO.getApiProvider());
                    preparedStatement.setString(4, apiInfoDTO.getApiName());
                    preparedStatement.setString(5, apiInfoDTO.getApiVersion());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_UUID_BY_THE_IDENTIFIER),
                        e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(UPDATE_UUID_BY_THE_IDENTIFIER), e);
        }
    }

    public Set<String> retrieveListOfEndpointCertificateAliases() throws APIMigrationException {

        Set<String> aliasSet = new HashSet<>();
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement preparedStatement =
                         connection.prepareStatement(RETRIEVE_ENDPOINT_CERTIFICATE_ALIASES)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        aliasSet.add(resultSet.getString("ALIAS"));
                    }
                }
            }
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(RETRIEVE_ENDPOINT_CERTIFICATE_ALIASES),
                    e);
        }
        return aliasSet;
    }

    public void updateEndpointCertificates(Map<String, String> certificateMap) throws APIMigrationException {

        if (certificateMap == null || certificateMap.isEmpty()) {
            return;
        }
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_ENDPOINT_CERTIFICATES)) {
                for (Map.Entry<String, String> certificateMapEntry : certificateMap.entrySet()) {
                    preparedStatement.setBinaryStream(1, getInputStream(certificateMapEntry.getValue()));
                    preparedStatement.setString(2, certificateMapEntry.getKey());
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }

        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing: ".concat(RETRIEVE_ENDPOINT_CERTIFICATE_ALIASES),
                    e);
        }
    }

    private InputStream getInputStream(String value) {

        byte[] cert = value.getBytes(StandardCharsets.UTF_8);
        return new ByteArrayInputStream(cert);
    }

    /**
     * This method is used to update the AM_API_PRODUCT_MAPPING_TABLE
     * @throws APIMigrationException
     */
    public void updateProductMappings() throws APIMigrationException {
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            connection.setAutoCommit(false);
            // Retrieve All Product Resources url mapping ids
            PreparedStatement getProductMappingsStatement = connection.prepareStatement(GET_CURRENT_API_PRODUCT_RESOURCES);
            Map<Integer,Integer> urlMappingIds = new HashMap<>();
            try (ResultSet rs = getProductMappingsStatement.executeQuery()) {
                while (rs.next()) {
                    urlMappingIds.put(rs.getInt(1),rs.getInt(2));
                }
            }
            for (Map.Entry<Integer, Integer> entry : urlMappingIds.entrySet())   {
                int urlMappingId = entry.getKey();
                int productId = entry.getValue();
                // Adding to AM_API_URL_MAPPING table
                PreparedStatement getURLMappingsStatement = connection
                        .prepareStatement(GET_URL_MAPPINGS_WITH_SCOPE_BY_URL_MAPPING_ID);
                getURLMappingsStatement.setInt(1, urlMappingId);
                List<URITemplate> urlMappingList = new ArrayList<>();
                try (ResultSet rs = getURLMappingsStatement.executeQuery()) {
                    while (rs.next()) {
                        String script = null;
                        URITemplate uriTemplate = new URITemplate();
                        uriTemplate.setHTTPVerb(rs.getString(1));
                        uriTemplate.setAuthType(rs.getString(2));
                        uriTemplate.setUriTemplate(rs.getString(3));
                        uriTemplate.setThrottlingTier(rs.getString(4));
                        InputStream mediationScriptBlob = rs.getBinaryStream(5);
                        if (mediationScriptBlob != null) {
                            script = getStringFromInputStream(mediationScriptBlob);
                        }
                        uriTemplate.setMediationScript(script);
                        if (!StringUtils.isEmpty(rs.getString(6))) {
                            Scope scope = new Scope();
                            scope.setKey(rs.getString(6));
                            uriTemplate.setScope(scope);
                        }
                        if (rs.getInt(7) != 0) {
                            // Adding api id to uri template id just to store value
                            uriTemplate.setId(rs.getInt(7));
                        }
                        urlMappingList.add(uriTemplate);
                    }
                }

                Map<String, URITemplate> uriTemplateMap = new HashMap<>();
                for (URITemplate urlMapping : urlMappingList) {
                    if (urlMapping.getScope() != null) {
                        URITemplate urlMappingNew = urlMapping;
                        URITemplate urlMappingExisting = uriTemplateMap.get(urlMapping.getUriTemplate()
                                + urlMapping.getHTTPVerb());
                        if (urlMappingExisting != null && urlMappingExisting.getScopes() != null) {
                            if (!urlMappingExisting.getScopes().contains(urlMapping.getScope())) {
                                urlMappingExisting.setScopes(urlMapping.getScope());
                                uriTemplateMap.put(urlMappingExisting.getUriTemplate() + urlMappingExisting.getHTTPVerb(),
                                        urlMappingExisting);
                            }
                        } else {
                            urlMappingNew.setScopes(urlMapping.getScope());
                            uriTemplateMap.put(urlMappingNew.getUriTemplate() + urlMappingNew.getHTTPVerb(), urlMappingNew);
                        }
                    } else if (urlMapping.getId() != 0) {
                        URITemplate urlMappingExisting = uriTemplateMap.get(urlMapping.getUriTemplate()
                                + urlMapping.getHTTPVerb());
                        if (urlMappingExisting == null) {
                            uriTemplateMap.put(urlMapping.getUriTemplate() + urlMapping.getHTTPVerb(), urlMapping);
                        }
                    } else {
                        uriTemplateMap.put(urlMapping.getUriTemplate() + urlMapping.getHTTPVerb(), urlMapping);
                    }
                }

                PreparedStatement insertURLMappingsStatement = connection
                        .prepareStatement(INSERT_URL_MAPPINGS);
                for (URITemplate urlMapping : uriTemplateMap.values()) {
                    insertURLMappingsStatement.setInt(1, urlMapping.getId());
                    insertURLMappingsStatement.setString(2, urlMapping.getHTTPVerb());
                    insertURLMappingsStatement.setString(3, urlMapping.getAuthType());
                    insertURLMappingsStatement.setString(4, urlMapping.getUriTemplate());
                    insertURLMappingsStatement.setString(5, urlMapping.getThrottlingTier());
                    insertURLMappingsStatement.setString(6, String.valueOf(productId));
                    insertURLMappingsStatement.addBatch();
                }
                insertURLMappingsStatement.executeBatch();

                // Add to AM_API_RESOURCE_SCOPE_MAPPING table and to AM_API_PRODUCT_MAPPING
                PreparedStatement getRevisionedURLMappingsStatement = connection
                        .prepareStatement(GET_URL_MAPPINGS_ID);
                PreparedStatement insertScopeResourceMappingStatement = connection
                        .prepareStatement(INSERT_SCOPE_RESOURCE_MAPPING);
                PreparedStatement insertProductResourceMappingStatement = connection
                        .prepareStatement(ADD_PRODUCT_RESOURCE_MAPPING_SQL);
                for (URITemplate urlMapping : uriTemplateMap.values()) {
                    getRevisionedURLMappingsStatement.setInt(1, urlMapping.getId());
                    getRevisionedURLMappingsStatement.setString(2, urlMapping.getHTTPVerb());
                    getRevisionedURLMappingsStatement.setString(3, urlMapping.getAuthType());
                    getRevisionedURLMappingsStatement.setString(4, urlMapping.getUriTemplate());
                    getRevisionedURLMappingsStatement.setString(5, urlMapping.getThrottlingTier());
                    getRevisionedURLMappingsStatement.setString(6, String.valueOf(productId));
                    if (!urlMapping.getScopes().isEmpty()) {
                        try (ResultSet rs = getRevisionedURLMappingsStatement.executeQuery()) {
                            while (rs.next()) {
                                for (Scope scope : urlMapping.getScopes()) {
                                    insertScopeResourceMappingStatement.setString(1, scope.getKey());
                                    insertScopeResourceMappingStatement.setInt(2, rs.getInt(1));
                                    insertScopeResourceMappingStatement.setInt(3, MultitenantConstants.SUPER_TENANT_ID);
                                    insertScopeResourceMappingStatement.addBatch();
                                }
                            }
                        }
                    }
                    try (ResultSet rs = getRevisionedURLMappingsStatement.executeQuery()) {
                        while (rs.next()) {
                            insertProductResourceMappingStatement.setInt(1, productId);
                            insertProductResourceMappingStatement.setInt(2, rs.getInt(1));
                            insertProductResourceMappingStatement.setString(3, "Current API");
                            insertProductResourceMappingStatement.addBatch();
                        }
                    }
                }
                insertScopeResourceMappingStatement.executeBatch();
                insertProductResourceMappingStatement.executeBatch();
            }

            // Removing previous product entries from AM_API_PRODUCT_MAPPING table
            PreparedStatement removeURLMappingsStatement = connection.prepareStatement(REMOVE_PRODUCT_ENTRIES_IN_AM_API_PRODUCT_MAPPING);
            for (Map.Entry<Integer, Integer> entry : urlMappingIds.entrySet())   {
                int urlMappingId = entry.getKey();
                int productId = entry.getValue();
                removeURLMappingsStatement.setInt(1, urlMappingId);
                removeURLMappingsStatement.setInt(2, productId);
                removeURLMappingsStatement.addBatch();
            }
            removeURLMappingsStatement.executeBatch();
        } catch (SQLException e) {
            throw new APIMigrationException("SQLException when executing updateProductMappings", e);
        }
    }

    /**
     * Function converts IS to String
     * Used for handling blobs
     * @param is - The Input Stream
     * @return - The inputStream as a String
     */
    public static String getStringFromInputStream(InputStream is) throws APIMigrationException {
        String str = null;
        try {
            str = IOUtils.toString(is, "UTF-8");
        } catch (IOException e) {
            throw new APIMigrationException("Error occurred while converting input stream to string.", e);
        }
        return str;
    }

    /**
     * This method is used to read labels from the table AM_LABELS and access URLs from the table AM_LABEL_URLS
     *
     * @return List of LabelDTO containing label details and access URLs
     * @throws APIMigrationException if failed to read labels from the table
     */
    public List<LabelDTO> getLabels() throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_LABEL_SQL)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    Map<String, LabelDTO> labelMap = new HashMap<>();
                    while (resultSet.next()) {
                        String label_id = resultSet.getString("LABEL_ID");
                        if (labelMap.get(label_id) == null) {
                            LabelDTO labelDTO = new LabelDTO();
                            labelDTO.setLabelId(label_id);
                            labelDTO.setName(resultSet.getString("NAME"));
                            labelDTO.setDescription(resultSet.getString("DESCRIPTION"));
                            labelDTO.setTenantDomain(resultSet.getString("TENANT_DOMAIN"));
                            labelMap.put(label_id, labelDTO);
                        }
                        labelMap.get(label_id).getAccessUrls().add(resultSet.getString("ACCESS_URL"));
                    }
                    return new ArrayList<>(labelMap.values());
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from AM_LABELS and AM_LABEL_URLS", ex);
        }
    }

    /**
     * This method is used to insert dynamic environments to the table AM_GATEWAY_ENVIRONMENT
     * and VHosts to the table AM_GW_VHOST
     *
     * @param environments List of environments to be added to the table
     * @throws APIMigrationException if failed to add environments and vhosts
     */
    public void addDynamicGatewayEnvironments(List<GatewayEnvironmentDTO> environments) throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            conn.setAutoCommit(false);
            String dbProductName = conn.getMetaData().getDatabaseProductName();
            try (PreparedStatement ps = conn.prepareStatement(INSERT_GATEWAY_ENVIRONMENT,
                    new String[]{DBUtils.getConvertedAutoGeneratedColumnName(dbProductName, "ID")})) {
                for (GatewayEnvironmentDTO environment : environments) {
                    ps.setString(1, environment.getUuid());
                    ps.setString(2, environment.getName());
                    ps.setString(3, environment.getTenantDomain());
                    ps.setString(4, environment.getDisplayName());
                    ps.setString(5, environment.getDescription());
                    ps.executeUpdate();

                    ResultSet rs = ps.getGeneratedKeys();
                    int id = -1;
                    if (rs.next()) {
                        id = rs.getInt(1);
                    }
                    addGatewayVhosts(conn, id, environment.getVhosts());
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw new APIMigrationException("Failed to add data to AM_GATEWAY_ENVIRONMENT table : ", ex);
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to add data to AM_GATEWAY_ENVIRONMENT table : ", ex);
        }
    }

    /**
     * This method is used to insert Vhosts to the table AM_GW_VHOST
     *
     * @param connection DB connection
     * @param id ID of the dynamic environment
     * @param vhosts VHost to be added
     * @throws APIMigrationException if failed to insert data
     */
    private void addGatewayVhosts(Connection connection, int id, List<VHost> vhosts) throws
            APIMigrationException {
        try (PreparedStatement prepStmt = connection.prepareStatement(INSERT_VHOST)) {
            for (VHost vhost : vhosts) {
                prepStmt.setInt(1, id);
                prepStmt.setString(2, vhost.getHost());
                prepStmt.setString(3, vhost.getHttpContext());
                prepStmt.setString(4, vhost.getHttpPort().toString());
                prepStmt.setString(5, vhost.getHttpsPort().toString());
                prepStmt.setString(6, vhost.getWsPort().toString());
                prepStmt.setString(7, vhost.getWssPort().toString());
                prepStmt.addBatch();
            }
            prepStmt.executeBatch();
        } catch (SQLException e) {
            throw new APIMigrationException("Failed to add data to AM_GW_VHOST table : ", e);
        }
    }

    /**
     * Drop AM_LABELS and AM_LABEL_URLS tables
     *
     * @throws APIMigrationException if failed to drop tables
     */
    public void dropLabelTable () throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (
                    PreparedStatement ps1 = conn.prepareStatement(DROP_AM_LABEL_URLS_TABLE);
                    PreparedStatement ps2 = conn.prepareStatement(DROP_AM_LABELS_TABLE)
            ) {
                ps1.executeUpdate();
                ps2.executeUpdate();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw new APIMigrationException("Failed to drop tables AM_LABELS and AM_LABEL_URLS : ", ex);
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to drop tables AM_LABELS and AM_LABEL_URLS : ", ex);
        }
    }

    public List<Integer> retrieveWebSocketAPIs() throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(GET_API_ID_OF_WS_APIS)) {
                try (ResultSet resultSet = ps.executeQuery()) {
                    List<Integer> apis = new ArrayList<>();
                    while (resultSet.next()) {
                        int apiId = resultSet.getInt("API_ID");
                        apis.add(apiId);
                    }
                    return apis;
                }
            }
        } catch (SQLException ex) {
            throw new APIMigrationException("Failed to get data from AM_API table", ex);
        }
    }

    /**
     * This method is used to insert data to add default URL Mappings of WS APIs
     *
     * @param wsApiIds
     * @throws APIMigrationException
     */
    public void addDefaultURLTemplatesForWSAPIs(List<Integer> wsApiIds) throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_URL_MAPPINGS_FOR_WS_APIS)) {
                for (int apiId : wsApiIds) {
                    ps.setInt(1, apiId);
                    ps.setString(2, "SUBSCRIBE");
                    ps.setString(3, Constants.AUTH_APPLICATION_OR_USER_LEVEL_TOKEN);
                    ps.setString(4, Constants.API_DEFAULT_URI_TEMPLATE);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new APIMigrationException("Error while adding URL template(s) to the database " + e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("Error while adding URL template(s) to the database " + e);
        }
    }

    /**
     * This method is used to remove previous entries from URL Mapping table of WS APIs
     *
     * @param wsApiIds
     * @throws APIMigrationException
     */
    public void removePreviousURLTemplatesForWSAPIs(List<Integer> wsApiIds) throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(REMOVE_URL_MAPPINGS_FOR_WS_APIS)) {
                for (int apiId : wsApiIds) {
                    ps.setInt(1, apiId);
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw new APIMigrationException("Error while removing URL template(s) from the database " + e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("Error while removing URL template(s) from the database " + e);
        }
    }

    public void addKeyManagerConfiguration(KeyManagerConfigurationDTO keyManagerConfigurationDTO)
            throws APIMigrationException {

        try (Connection conn = APIMgtDBUtil.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement preparedStatement = conn
                    .prepareStatement(ADD_KEY_MANAGER)) {
                preparedStatement.setString(1, keyManagerConfigurationDTO.getUuid());
                preparedStatement.setString(2, keyManagerConfigurationDTO.getName());
                preparedStatement.setString(3, keyManagerConfigurationDTO.getDescription());
                preparedStatement.setString(4, keyManagerConfigurationDTO.getType());
                String configurationJson = new Gson().toJson(keyManagerConfigurationDTO.getAdditionalProperties());
                preparedStatement.setBinaryStream(5, new ByteArrayInputStream(configurationJson.getBytes()));
                preparedStatement.setString(6, keyManagerConfigurationDTO.getTenantDomain());
                preparedStatement.setBoolean(7, keyManagerConfigurationDTO.isEnabled());
                preparedStatement.setString(8, keyManagerConfigurationDTO.getDisplayName());
                preparedStatement.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                if (e instanceof SQLIntegrityConstraintViolationException) {
                    if (getKeyManagerConfigurationByName(conn, keyManagerConfigurationDTO.getTenantDomain(),
                            keyManagerConfigurationDTO.getName()) != null) {
                        log.warn(keyManagerConfigurationDTO.getName() + " Key Manager Already Registered in tenant" +
                                keyManagerConfigurationDTO.getTenantDomain());
                    } else {
                        throw new APIMigrationException("Error while Storing key manager configuration with name " +
                                keyManagerConfigurationDTO.getName() + " in tenant " +
                                keyManagerConfigurationDTO.getTenantDomain(), e);
                    }
                }
            }
        } catch (SQLException | IOException e) {
            throw new APIMigrationException(
                    "Error while Storing key manager configuration with name " + keyManagerConfigurationDTO.getName() +
                            " in tenant " + keyManagerConfigurationDTO.getTenantDomain(), e);
        }
    }

    private KeyManagerConfigurationDTO getKeyManagerConfigurationByName(Connection connection, String tenantDomain,
            String name)
            throws SQLException, IOException {

        final String query = "SELECT * FROM AM_KEY_MANAGER WHERE NAME = ? AND TENANT_DOMAIN = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, name);
            preparedStatement.setString(2, tenantDomain);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    KeyManagerConfigurationDTO keyManagerConfigurationDTO = new KeyManagerConfigurationDTO();
                    String uuid = resultSet.getString("UUID");
                    keyManagerConfigurationDTO.setUuid(uuid);
                    keyManagerConfigurationDTO.setName(resultSet.getString("NAME"));
                    keyManagerConfigurationDTO.setDisplayName(resultSet.getString("DISPLAY_NAME"));
                    keyManagerConfigurationDTO.setDescription(resultSet.getString("DESCRIPTION"));
                    keyManagerConfigurationDTO.setType(resultSet.getString("TYPE"));
                    keyManagerConfigurationDTO.setEnabled(resultSet.getBoolean("ENABLED"));
                    keyManagerConfigurationDTO.setTenantDomain(tenantDomain);
                    return keyManagerConfigurationDTO;
                }
            }
        }
        return null;
    }

    public KeyManagerConfigurationDTO getKeyManagerConfigurationByName(String tenantDomain, String name)
            throws APIMigrationException {
        try (Connection conn = APIMgtDBUtil.getConnection()) {
            return getKeyManagerConfigurationByName(conn, tenantDomain, name);
        } catch (SQLException | IOException e) {
            throw new APIMigrationException(
                    "Error while retrieving key manager configuration for " + name + " in tenant " + tenantDomain, e);
        }
    }

    /**
     * Get a provided api uuid is in the revision db table
     *
     * @return String apiUUID
     * @throws APIMigrationException if an error occurs while checking revision table
     */
    public APIRevision checkAPIUUIDIsARevisionUUID(String apiUUID) throws APIMigrationException {

        try (Connection connection = APIMgtDBUtil.getConnection();
             PreparedStatement statement = connection.prepareStatement(GET_REVISION_APIID_BY_REVISION_UUID)) {
            statement.setString(1, apiUUID);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    APIRevision apiRevision = new APIRevision();
                    apiRevision.setApiUUID(rs.getString("API_UUID"));
                    apiRevision.setId(rs.getInt("ID"));
                    apiRevision.setRevisionUUID(apiUUID);
                    return apiRevision;
                }
            }
        } catch (SQLException e) {
            throw new APIMigrationException("WSO2 API-M Migration Task : Failed to search UUID: " + apiUUID +
                    " in the revision db table", e);
        }
        return null;
    }
}
