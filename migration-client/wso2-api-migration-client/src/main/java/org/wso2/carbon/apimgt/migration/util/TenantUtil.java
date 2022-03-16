/*
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.apimgt.migration.util;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.base.MultitenantConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.core.multitenancy.utils.TenantAxisUtils;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;

public class TenantUtil {

    private static final Log log = LogFactory.getLog(TenantUtil.class);

    /**
     * Load tenant axis configurations.
     *
     * @param tenant loading tenant
     */
    public static void loadTenantConfigBlockingMode(Tenant tenant) {

        try {
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenant.getDomain(), true);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenant.getId());
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setUsername(getTenantAdminUserName(tenant.getDomain()));
            ConfigurationContext ctx = ServiceReferenceHolder.getContextService().getServerConfigContext();
            TenantAxisUtils.getTenantAxisConfiguration(tenant.getDomain(), ctx);

        } catch (Exception e) {
            log.error("Error while creating axis configuration for tenant " + tenant.getDomain(), e);
        }
        PrivilegedCarbonContext.endTenantFlow();
    }

    /**
     * Get tenant admin user name.
     *
     * @param tenantDomain tenant domain
     * @return admin user name
     * @throws APIManagementException if an error occurs
     */
    public static String getTenantAdminUserName(String tenantDomain) throws APIManagementException {

        try {
            int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager().
                    getTenantId(tenantDomain);
            PrivilegedCarbonContext.startTenantFlow();
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantDomain(tenantDomain);
            PrivilegedCarbonContext.getThreadLocalCarbonContext().setTenantId(tenantId);
            String adminUserName = ServiceReferenceHolder.getInstance().getRealmService().getTenantUserRealm(tenantId)
                    .getRealmConfiguration().getAdminUserName();
            if (!tenantDomain.contentEquals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                return adminUserName.concat("@").concat(tenantDomain);
            }
            return adminUserName;
        } catch (UserStoreException e) {
            throw new APIManagementException("Error in getting tenant admin username", e);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }
}
