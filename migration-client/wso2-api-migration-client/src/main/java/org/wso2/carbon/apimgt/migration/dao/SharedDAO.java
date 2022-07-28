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


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.dto.UserRoleFromPermissionDTO;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.SharedDBUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represent the SharedDAO.
 */
public class SharedDAO {
    private static final Log log = LogFactory.getLog(SharedDAO.class);
    private static SharedDAO INSTANCE = null;

    private SharedDAO() {
    }

    public List<UserRoleFromPermissionDTO> getRoleNamesMatchingPermission(String permission, int tenantId) throws APIMigrationException {
        List<UserRoleFromPermissionDTO> userRoleFromPermissionList = new ArrayList<UserRoleFromPermissionDTO>();

        String sqlQuery =
                " SELECT " +
                "   UM_ROLE_NAME, UM_DOMAIN_NAME " +
                " FROM "+
                "   UM_ROLE_PERMISSION, UM_PERMISSION, UM_DOMAIN " +
                " WHERE " +
                "   UM_ROLE_PERMISSION.UM_PERMISSION_ID=UM_PERMISSION.UM_ID " +
                "   AND " +
                "   UM_ROLE_PERMISSION.UM_DOMAIN_ID=UM_DOMAIN.UM_DOMAIN_ID " +
                "   AND " +
                "   UM_RESOURCE_ID = ? " +
                "   AND " +
                "   UM_ROLE_PERMISSION.UM_TENANT_ID = ?";

        try (Connection conn = SharedDBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlQuery)) {

            ps.setString(1, permission);
            ps.setInt(2, tenantId);

            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    String userRoleName = resultSet.getString(Constants.UM_ROLE_NAME);
                    String userRoleDomainName = resultSet.getString(Constants.UM_DOMAIN_NAME);
                    UserRoleFromPermissionDTO userRoleFromPermissionDTO = new UserRoleFromPermissionDTO();
                    userRoleFromPermissionDTO.setUserRoleName(userRoleName);
                    userRoleFromPermissionDTO.setUserRoleDomainName(userRoleDomainName);
                    userRoleFromPermissionList.add(userRoleFromPermissionDTO);

                    log.info("WSO2 API-M Migration Task : User role name: " + userRoleName + ", User domain name: " + userRoleDomainName
                            + " retrieved for " + tenantId);
                }
            } catch (SQLException e) {
                throw new APIMigrationException("Failed to get the result set.", e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("Failed to get Roles matching the permission " + permission +
                    " and tenant " + tenantId, e);
        }
        return userRoleFromPermissionList;
    }

    public List<UserRoleFromPermissionDTO> getRoleNamesMatchingPermissions(String permissions, int tenantId) throws APIMigrationException {
        List<UserRoleFromPermissionDTO> userRoleFromPermissionList = new ArrayList<UserRoleFromPermissionDTO>();

        String sqlQuery =
                " SELECT " +
                "   DISTINCT UM_ROLE_NAME, UM_DOMAIN_NAME " +
                " FROM " +
                "   UM_ROLE_PERMISSION, UM_PERMISSION, UM_DOMAIN " +
                " WHERE " +
                "   UM_ROLE_PERMISSION.UM_PERMISSION_ID=UM_PERMISSION.UM_ID " +
                "   AND " +
                "   UM_ROLE_PERMISSION.UM_DOMAIN_ID=UM_DOMAIN.UM_DOMAIN_ID " +
                "   AND " +
                "   UM_RESOURCE_ID IN (" + permissions + ")" +
                "   AND " +
                "   UM_ROLE_PERMISSION.UM_TENANT_ID = ?";

        try (Connection conn = SharedDBUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlQuery)) {

            ps.setInt(1, tenantId);

            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    String userRoleName = resultSet.getString(Constants.UM_ROLE_NAME);
                    String userRoleDomainName = resultSet.getString(Constants.UM_DOMAIN_NAME);
                    UserRoleFromPermissionDTO userRoleFromPermissionDTO = new UserRoleFromPermissionDTO();
                    userRoleFromPermissionDTO.setUserRoleName(userRoleName);
                    userRoleFromPermissionDTO.setUserRoleDomainName(userRoleDomainName);
                    userRoleFromPermissionList.add(userRoleFromPermissionDTO);

                    log.info("WSO2 API-M Migration Task : User role name: " + userRoleName + ", User domain name: "
                            + userRoleDomainName + " retrieved for " + tenantId);
                }
            } catch (SQLException e) {
                throw new APIMigrationException("Failed to get the result set.", e);
            }
        } catch (SQLException e) {
            throw new APIMigrationException("Failed to get Roles matching the permission " + permissions +
                    " and tenant " + tenantId, e);
        }
        return userRoleFromPermissionList;
    }

    /**
     * Method to get the instance of the SharedDAO.
     *
     * @return {@link SharedDAO} instance
     */
    public static SharedDAO getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SharedDAO();
        }
        return INSTANCE;
    }
}
