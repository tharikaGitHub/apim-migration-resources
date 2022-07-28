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

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.Scope;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dao.ScopesDAO;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.identity.core.util.IdentityIOStreamUtils;
import org.wso2.carbon.identity.oauth.common.OAuthConstants;
import org.wso2.carbon.utils.CarbonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * This class used to migrate scopes from IDN_OAUTH2_SCOPE table to AM_SCOPE table at server startup
 */
public class IdentityScopeMigration {

    private static final Log log = LogFactory.getLog(IdentityScopeMigration.class);
    private static final String SELECT_SCOPES_QUERY_LEFT = "SELECT IDN_OAUTH2_SCOPE.SCOPE_ID AS SCOPE_ID," +
            "IDN_OAUTH2_SCOPE.NAME AS SCOPE_KEY,IDN_OAUTH2_SCOPE.DISPLAY_NAME AS DISPLAY_NAME,IDN_OAUTH2_SCOPE" +
            ".DESCRIPTION AS DESCRIPTION,IDN_OAUTH2_SCOPE.TENANT_ID AS TENANT_ID," +
            "SCOPE_TYPE AS SCOPE_TYPE,IDN_OAUTH2_SCOPE_BINDING.SCOPE_BINDING AS SCOPE_BINDING " +
            "FROM IDN_OAUTH2_SCOPE LEFT JOIN IDN_OAUTH2_SCOPE_BINDING ON IDN_OAUTH2_SCOPE" +
            ".SCOPE_ID=IDN_OAUTH2_SCOPE_BINDING.SCOPE_ID WHERE IDN_OAUTH2_SCOPE.SCOPE_TYPE = 'OAUTH2' " +
            "AND IDN_OAUTH2_SCOPE.NAME NOT IN (SCOPE_SKIP_LIST)";
    private static final String IDENTITY_PATH = "identity";
    private static final String NAME = "name";

    public void migrateScopes() throws APIManagementException {

        Map<Integer, Map<String, Scope>> scopesMap = new HashMap<>();
        boolean scopesMigrated = isScopesMigrated();
        if (!scopesMigrated) {
            String query = SELECT_SCOPES_QUERY_LEFT;
            List<String> identityScopes = retrieveIdentityScopes();
            query = query.replaceAll("SCOPE_SKIP_LIST", StringUtils.repeat("?", ",", identityScopes.size()));
            try (Connection connection = APIMgtDBUtil.getConnection()) {
                try (PreparedStatement preparedStatement = connection
                        .prepareStatement(query)) {
                    for (int i = 0; i < identityScopes.size(); i++) {
                        preparedStatement.setString(i + 1, identityScopes.get(i));
                    }
                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            int scopeId = resultSet.getInt("SCOPE_ID");
                            String scopeKey = resultSet.getString("SCOPE_KEY");
                            String displayName = resultSet.getString("DISPLAY_NAME");
                            String description = resultSet.getString("DESCRIPTION");
                            int tenantId = resultSet.getInt("TENANT_ID");
                            String scopeBinding = resultSet.getString("SCOPE_BINDING");
                            Map<String, Scope> scopeMap = scopesMap.computeIfAbsent(tenantId,
                                    k -> new HashMap<>());
                            Scope scope = scopeMap.get(scopeKey);
                            if (scope == null) {
                                scope = new Scope();
                                scope.setId(Integer.toString(scopeId));
                                scope.setKey(scopeKey);
                                scope.setName(displayName);
                                scope.setDescription(description);
                                scopeMap.put(scopeKey, scope);
                            }
                            String roles = scope.getRoles();
                            if (StringUtils.isNotEmpty(scopeBinding)) {
                                if (StringUtils.isEmpty(roles)) {
                                    scope.setRoles(scopeBinding);
                                } else {
                                    scope.setRoles(scope.getRoles().concat(",").concat(scopeBinding));
                                }
                            }
                        }
                    }
                }
                for (Map.Entry<Integer, Map<String, Scope>> scopesMapEntry : scopesMap.entrySet()) {
                    ScopesDAO scopesDAO = ScopesDAO.getInstance();
                    Map<String, Scope> scopeMap = scopesMapEntry.getValue();
                    if (scopeMap != null) {
                        Set<Scope> scopeSet = new HashSet<>(scopeMap.values());
                        scopesDAO.addScopes(scopeSet, scopesMapEntry.getKey());
                    }
                }
            } catch (SQLException e) {
                throw new APIManagementException("Error while retrieving database connection", e);
            }
        }
    }

    private boolean isScopesMigrated() throws APIManagementException {

        try (Connection connection = APIMgtDBUtil.getConnection()) {
            try (PreparedStatement preparedStatement = connection
                    .prepareStatement("SELECT 1 FROM AM_SCOPE WHERE SCOPE_TYPE = ?")) {
                preparedStatement.setString(1, APIConstants.DEFAULT_SCOPE_TYPE);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    return true;
                }
            }
        } catch (SQLException e) {
            throw new APIManagementException("Error while retrieving database connection", e);
        }
        return false;
    }

    private static List<String> retrieveIdentityScopes() {

        List<String> scopes = new ArrayList<>();
        String configDirPath = CarbonUtils.getCarbonConfigDirPath();
        String confXml = Paths.get(configDirPath, IDENTITY_PATH, OAuthConstants.OAUTH_SCOPE_BINDING_PATH)
                .toString();
        File configFile = new File(confXml);
        if (!configFile.exists()) {
            log.info("WSO2 API-M Migration Task : OAuth scope binding File is not present at: " + confXml);
            return new ArrayList<>();
        }

        XMLStreamReader parser = null;
        InputStream stream = null;

        try {
            stream = new FileInputStream(configFile);
            parser = XMLInputFactory.newInstance()
                    .createXMLStreamReader(stream);
            StAXOMBuilder builder = new StAXOMBuilder(parser);
            OMElement documentElement = builder.getDocumentElement();
            Iterator iterator = documentElement.getChildElements();
            while (iterator.hasNext()) {
                OMElement omElement = (OMElement) iterator.next();
                String scopeName = omElement.getAttributeValue(new QName(
                        NAME));
                scopes.add(scopeName);
            }
        } catch (XMLStreamException e) {
            log.info("WSO2 API-M Migration Task : Error while loading scope config.", e);
        } catch (FileNotFoundException e) {
            log.info("WSO2 API-M Migration Task : Error while loading email config.", e);
        } finally {
            try {
                if (parser != null) {
                    parser.close();
                }
                if (stream != null) {
                    IdentityIOStreamUtils.closeInputStream(stream);
                }
            } catch (XMLStreamException e) {
                log.error("WSO2 API-M Migration Task : Error while closing XML stream", e);
            }
        }
        return scopes;
    }

}

