/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.apimgt.migration.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONObject;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.migration.APIMigrationException;
import org.wso2.carbon.apimgt.migration.client.sp_migration.APIMStatMigrationException;
import org.wso2.carbon.apimgt.migration.util.RegistryService;
import org.wso2.carbon.user.api.Tenant;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;

import java.sql.SQLException;
import java.util.List;

public class MigrateFrom200 extends MigrationClientBase implements MigrationClient {
    private static final Log log = LogFactory.getLog(MigrateFrom200.class);
    private RegistryService registryService;

    public MigrateFrom200(String tenantArguments, String blackListTenantArguments, String tenantRange,
                          RegistryService registryService, TenantManager tenantManager)
            throws UserStoreException, APIMigrationException {
        super(tenantArguments, blackListTenantArguments, tenantRange, tenantManager);
        this.registryService = registryService;
    }

    @Override
    public void databaseMigration() throws APIMigrationException, SQLException {
    }

    @Override
    public void registryResourceMigration() throws APIMigrationException {
        rxtMigration(registryService);
        updateGenericAPIArtifacts(registryService);
        migrateFaultSequencesInRegistry(registryService);
        addDefaultRoleCreationConfig();
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
    public void scopeMigration() throws APIMigrationException {
    }

    @Override
    public void spMigration() throws APIMigrationException {
    }

    @Override
    public void updateScopeRoleMappings() throws APIMigrationException {
    }

    @Override
    public void updateAPIPropertyVisibility() throws APIMigrationException {
    }

    @Override
    public void updateAPITypeInDB() throws APIMigrationException {
    }

    public void addDefaultRoleCreationConfig() throws APIMigrationException {
        log.info("Add config in tenant-conf.json to enable default roles creation.");
        for (Tenant tenant : getTenantsArray()) {
            try {
                registryService.startTenantFlow(tenant);
                log.info("Updating tenant-conf.json of tenant " + tenant.getId() + '(' +
                        tenant.getDomain() + ')');
                // Retrieve the tenant-conf.json of the corresponding tenant
                JSONObject tenantConf = APIUtil.getTenantConfig(tenant.getDomain());
                if (tenantConf.get(APIConstants.API_TENANT_CONF_DEFAULT_ROLES) == null) {
                    JSONObject defaultRoleConfig = new JSONObject();
                    JSONObject publisherRole = new JSONObject();
                    publisherRole.put("CreateOnTenantLoad", true);
                    publisherRole.put("RoleName", "Internal/publisher");

                    JSONObject creatorRole = new JSONObject();
                    creatorRole.put("CreateOnTenantLoad", true);
                    creatorRole.put("RoleName", "Internal/creator");

                    JSONObject subscriberRole = new JSONObject();
                    subscriberRole.put("CreateOnTenantLoad", true);

                    defaultRoleConfig.put("PublisherRole", publisherRole);
                    defaultRoleConfig.put("CreatorRole", creatorRole);
                    defaultRoleConfig.put("SubscriberRole", subscriberRole);
                    tenantConf.put("DefaultRoles", defaultRoleConfig);
                    ObjectMapper mapper = new ObjectMapper();
                    String formattedTenantConf = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tenantConf);

                    APIUtil.updateTenantConf(formattedTenantConf, tenant.getDomain());
                    log.info("Updated tenant-conf.json for tenant " + tenant.getId() + '(' + tenant.getDomain() + ')'
                            + "\n" + formattedTenantConf);

                    log.info("End updating tenant-conf.json to add default role creation configuration for tenant "
                            + tenant.getId() + '(' + tenant.getDomain() + ')');
                }
            } catch (APIManagementException e) {
                log.error("Error while retrieving the tenant-conf.json of tenant " + tenant.getId(), e);
            } catch (JsonProcessingException e) {
                log.error("Error while formatting tenant-conf.json of tenant " + tenant.getId(), e);
            } finally {
                registryService.endTenantFlow();
            }
        }
    }
}
