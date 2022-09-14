package org.wso2.carbon.apimgt.migration.validator.dao;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

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
}
