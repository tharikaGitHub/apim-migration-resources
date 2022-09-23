/*
 * Copyright (c) 2022, WSO2 LLC. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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
package org.wso2.carbon.apimgt.migration.validator.dao;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.validator.dto.ApplicationDTO;
import org.wso2.carbon.apimgt.migration.validator.dto.ApplicationKeyMappingDTO;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class ApiMgtDAO {
    private static final Log log = LogFactory.getLog(ApiMgtDAO.class);
    private static ApiMgtDAO INSTANCE = null;

    public static ApiMgtDAO getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ApiMgtDAO();
        }
        return INSTANCE;
    }

    public int getAPIID(String provider, String name, String version) throws SQLException {

        int id = -1;
        String getAPIQuery = SQLConstants.GET_API_ID_SQL;

        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement prepStmt = connection.prepareStatement(getAPIQuery)) {
                prepStmt.setString(1, APIUtil.replaceEmailDomainBack(provider));
                prepStmt.setString(2, name);
                prepStmt.setString(3, version);
                try (ResultSet rs = prepStmt.executeQuery()) {
                    if (rs.next()) {
                        id = rs.getInt("API_ID");
                    }
                }
            }
        }
        return id;
    }

    public Set<URITemplate> getURITemplatesByAPIID(int apiId) {

        Set<URITemplate> urlTemplates = new HashSet<>();

        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(SQLConstants.GET_URL_TEMPLATES_BY_API_ID_SQL_260)) {
                ps.setInt(1, apiId);
                try (ResultSet resultSet = ps.executeQuery();) {
                    while (resultSet.next()) {
                        String script = null;
                        URITemplate uriTemplate = new URITemplate();
                        uriTemplate.setUriTemplate(resultSet.getString("URL_PATTERN"));
                        uriTemplate.setHTTPVerb(resultSet.getString("HTTP_METHOD"));
                        uriTemplate.setAuthType(resultSet.getString("AUTH_SCHEME"));
                        uriTemplate.setThrottlingTier(resultSet.getString("THROTTLING_TIER"));
                        InputStream mediationScriptBlob = resultSet.getBinaryStream("MEDIATION_SCRIPT");
                        if (mediationScriptBlob != null) {
                            script = APIMgtDBUtil.getStringFromInputStream(mediationScriptBlob);
                            if (script.isEmpty()) {
                                script = null;
                            }
                        }
                        uriTemplate.setMediationScript(script);
                        urlTemplates.add(uriTemplate);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error on retrieving URLTemplates for apiResourceLevelAuthSchemeValidation validation", e);
        }
        return urlTemplates;
    }

    public Set<ApplicationDTO> getAllApplications() {
        final String query = SQLConstants.GET_ALL_APPLICATIONS;
        Set<ApplicationDTO> applications = new HashSet<>();
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        ApplicationDTO application = new ApplicationDTO();
                        application.setApplicationId(resultSet.getInt("APPLICATION_ID"));
                        application.setName(resultSet.getString("NAME"));
                        application.setSubscriberId(resultSet.getString("SUBSCRIBER_ID"));
                        application.setStatus(resultSet.getString("APPLICATION_STATUS"));
                        application.setCreatedBy(resultSet.getString("CREATED_BY"));
                        application.setUuid(resultSet.getString("UUID"));
                        applications.add(application);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error on retrieving Applications for appThirdPartyKMValidation", e);
        }
        return applications;
    }

    public Set<ApplicationKeyMappingDTO> getKeyMappingFromApplicationId(int applicationId) {
        final String query = SQLConstants.GET_APPLICATION_KEY_MAPPING_BY_APP_ID_AND_KEY_TYPE;
        Set<ApplicationKeyMappingDTO> applicationKeyMappings = new HashSet<>();
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setInt(1, applicationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ApplicationKeyMappingDTO applicationKeyMappingDTO = new ApplicationKeyMappingDTO();
                        applicationKeyMappingDTO.setApplicationId(rs.getInt("APPLICATION_ID"));
                        applicationKeyMappingDTO.setConsumerKey(rs.getString("CONSUMER_KEY"));
                        applicationKeyMappingDTO.setKeyType(rs.getString("KEY_TYPE"));
                        applicationKeyMappingDTO.setState(rs.getString("STATE"));
                        String createMode = rs.getString("CREATE_MODE");
                        if (StringUtils.isEmpty(createMode)) {
                            createMode = APIConstants.OAuthAppMode.CREATED.name();
                        }
                        applicationKeyMappingDTO.setCreatedMode(createMode);
                        applicationKeyMappings.add(applicationKeyMappingDTO);
                    }
                }
            }
        } catch (SQLException e) {
            log.error("Error on retrieving Application Key Mappings for appThirdPartyKMValidation", e);
        }
        return applicationKeyMappings;
    }

    public boolean checkIfConsumerAppExists(String consumerKey) {
        final String query = SQLConstants.GET_IF_IDN_OAUTH_CONSUMER_APP_EXISTS;
        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, consumerKey);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            log.error("Error on retrieving Consumer Secret for appThirdPartyKMValidation", e);
        }
        return false;
    }
}
