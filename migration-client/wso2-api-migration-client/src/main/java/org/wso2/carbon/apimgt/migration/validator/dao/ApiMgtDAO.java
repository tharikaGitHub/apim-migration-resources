package org.wso2.carbon.apimgt.migration.validator.dao;

import org.wso2.carbon.apimgt.impl.dao.constants.SQLConstants;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ApiMgtDAO {
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
}
