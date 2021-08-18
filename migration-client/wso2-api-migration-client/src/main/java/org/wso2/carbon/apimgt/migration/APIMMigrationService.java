/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.apimgt.migration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.impl.utils.APIMgtDBUtil;
import org.wso2.carbon.apimgt.migration.client.MigrateFrom200;
import org.wso2.carbon.apimgt.migration.client.MigrateFrom210;
import org.wso2.carbon.apimgt.migration.client.MigrateFrom310;
import org.wso2.carbon.apimgt.migration.client.MigrationClient;
import org.wso2.carbon.apimgt.migration.client.MigrationClientFactory;
import org.wso2.carbon.apimgt.migration.client.MigrationExecutor;
import org.wso2.carbon.apimgt.migration.client.OauthAppAssignmentClient;
import org.wso2.carbon.apimgt.migration.client.ScopeRoleMappingPopulationClient;
import org.wso2.carbon.apimgt.migration.client.internal.ServiceHolder;
import org.wso2.carbon.apimgt.migration.client.sp_migration.APIMStatMigrationClient;
import org.wso2.carbon.apimgt.migration.client.sp_migration.APIMStatMigrationConstants;
import org.wso2.carbon.apimgt.migration.client.sp_migration.DBManager;
import org.wso2.carbon.apimgt.migration.client.sp_migration.DBManagerImpl;
import org.wso2.carbon.apimgt.migration.util.Constants;
import org.wso2.carbon.apimgt.migration.util.RegistryServiceImpl;
import org.wso2.carbon.apimgt.migration.util.SharedDBUtil;
import org.wso2.carbon.core.ServerStartupObserver;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;

import java.sql.SQLException;

public class APIMMigrationService implements ServerStartupObserver {

    private static final Log log = LogFactory.getLog(APIMMigrationService.class);
    private final String V200 = "2.0.0";
    private final String V210 = "2.1.0";
    private final String V220 = "2.2.0";
    private final String V250 = "2.5.0";
    private final String V260 = "2.6.0";
    private final String V310 = "3.1.0";
    private final String V300 = "3.0.0";

    @Override
    public void completingServerStartup() {
    }

    @Override
    public void completedServerStartup() {
        try {
            APIMgtDBUtil.initialize();
            SharedDBUtil.initialize();
        } catch (Exception e) {
            //APIMgtDBUtil.initialize() throws generic exception
            log.error("Error occurred while initializing DB Util ", e);
        }

        String migrateFromVersion = System.getProperty(Constants.ARG_MIGRATE_FROM_VERSION);
        String options = System.getProperty(Constants.ARG_OPTIONS);
        String specificVersion = System.getProperty(Constants.ARG_RUN_SPECIFIC_VERSION);
        String component = System.getProperty(Constants.ARG_COMPONENT);
        String tenants = System.getProperty(Constants.ARG_MIGRATE_TENANTS);
        String tenantRange = System.getProperty(Constants.ARG_MIGRATE_TENANTS_RANGE);
        String blackListTenants = System.getProperty(Constants.ARG_MIGRATE_BLACKLIST_TENANTS);
        boolean migrateAll = Boolean.parseBoolean(System.getProperty(Constants.ARG_MIGRATE_ALL));
        boolean cleanupNeeded = Boolean.parseBoolean(System.getProperty(Constants.ARG_CLEANUP));
        boolean isDBMigration = Boolean.parseBoolean(System.getProperty(Constants.ARG_MIGRATE_DB));
        boolean isRegistryMigration = Boolean.parseBoolean(System.getProperty(Constants.ARG_MIGRATE_REG));
        boolean isFileSystemMigration = Boolean.parseBoolean(System.getProperty(Constants.ARG_MIGRATE_FILE_SYSTEM));
        boolean isTriggerAPIIndexer = Boolean.parseBoolean(System.getProperty(Constants.ARG_TRIGGER_API_INDEXER));
        boolean isAccessControlMigration = Boolean.parseBoolean(System.getProperty(Constants.ARG_MIGRATE_ACCESS_CONTROL));
        boolean isStatMigration = Boolean.parseBoolean(System.getProperty(Constants.ARG_MIGRATE_STATS));
        boolean removeDecryptionFailedKeysFromDB = Boolean.parseBoolean(
                System.getProperty(Constants.ARG_REMOVE_DECRYPTION_FAILED_CONSUMER_KEYS_FROM_DB));
        boolean isSPMigration = Boolean.parseBoolean(System.getProperty(APIMStatMigrationConstants.ARG_MIGRATE_SP));
        boolean isSP_APP_Population = Boolean.parseBoolean(System.getProperty(Constants.ARG_POPULATE_SPAPP));
        boolean isScopeRoleMappingPopulation = Boolean.parseBoolean(System.getProperty(Constants.ARG_POPULATE_SCOPE_ROLE_MAPPING));
        boolean ignoreCrossTenantSubscriptions =
                Boolean.parseBoolean(System.getProperty(Constants.ARG_IGNORE_CROSS_TENANT_SUBSCRIPTIONS));
        boolean isSPAppAssignment = Boolean.parseBoolean(System.getProperty(Constants.ARG_ASSIGN_OAUTH_APPS_TO_OWNERS));

        try {
            RegistryServiceImpl registryService = new RegistryServiceImpl();
            TenantManager tenantManager = ServiceHolder.getRealmService().getTenantManager();
            if (isSPAppAssignment) {
                OauthAppAssignmentClient oauthAppAssignMentClient = new OauthAppAssignmentClient(tenants,
                        blackListTenants, tenantRange, tenantManager);
                oauthAppAssignMentClient.assignOauthAppToOwners();
            }
            //Check SP-Migration enabled
            if (isSPMigration) {
                log.info("----------------Migrating to WSO2 API Manager analytics 3.2.0");
                // Create a thread and wait till the APIManager DBUtils is initialized
                MigrationClient migrateStatDB = new APIMStatMigrationClient(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                DBManager dbManager = new DBManagerImpl();
                dbManager.initialize(migrateFromVersion);
                if(migrateFromVersion.equals(V310)){
                    dbManager.sortGraphQLOperation();
                } else if (migrateFromVersion.equals(V200) || migrateFromVersion.equals(V210) ||
                        migrateFromVersion.equals(V220) || migrateFromVersion.equals(V250)){
                     migrateStatDB.statsMigration();
                }
                log.info("------------------------------Stat migration completed----------------------------------");
                if (log.isDebugEnabled()) {
                    log.debug("----------------API Manager 3.2.0 Stat migration successfully completed------------");
                }
                //Check AccessControl-Migration enabled
            } else if (V200.equals(migrateFromVersion)) {
                MigrationClient migrateFrom200 = new MigrateFrom200(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Migrating WSO2 API Manager registry resources");
                migrateFrom200.registryResourceMigration();

                MigrationClient scopeRoleMappingPopulation = new ScopeRoleMappingPopulationClient(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Populating WSO2 API Manager Scope-Role Mapping");
                scopeRoleMappingPopulation.updateScopeRoleMappings();
                scopeRoleMappingPopulation.populateScopeRoleMapping();
                MigrationClient migrateFrom310 = new MigrateFrom310(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                migrateFrom310.checkCrossTenantAPISubscriptions(tenantManager, ignoreCrossTenantSubscriptions);
                migrateFrom310.scopeMigration();
                migrateFrom310.spMigration();
            } else if (V210.equals(migrateFromVersion) || V220.equals(migrateFromVersion) ||
                    V250.equals(migrateFromVersion) || V260.equals(migrateFromVersion)) {
                MigrationClient migrateFrom210 = new MigrateFrom210(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Migrating WSO2 API Manager registry resources");
                migrateFrom210.registryResourceMigration();
                MigrationClient scopeRoleMappingPopulation = new ScopeRoleMappingPopulationClient(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Populating WSO2 API Manager Scope-Role Mapping");
                scopeRoleMappingPopulation.updateScopeRoleMappings();
                scopeRoleMappingPopulation.populateScopeRoleMapping();
                log.info("Migrating WSO2 API Manager IP based block conditions");
                migrateFrom210.updateIpBasedBlockingConditions();
                log.info("Migrated Successfully to API Manager 3.1");
                log.info("Starting Migration from API Manager 3.1 to 3.2");
                MigrationClient migrateFrom310 = new MigrateFrom310(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                migrateFrom310.checkCrossTenantAPISubscriptions(tenantManager, ignoreCrossTenantSubscriptions);
                migrateFrom310.scopeMigration();
                migrateFrom310.spMigration();
                log.info("Migrated Successfully to 3.2");
            } else if (isScopeRoleMappingPopulation) {
                MigrationClient scopeRoleMappingPopulation = new ScopeRoleMappingPopulationClient(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Populating WSO2 API Manager Scope-Role Mapping");
                scopeRoleMappingPopulation.populateScopeRoleMapping();
            } else if(V310.equals(migrateFromVersion) || V300.equals(migrateFromVersion)) {
                MigrationClient migrateFrom310 = new MigrateFrom310(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                migrateFrom310.checkCrossTenantAPISubscriptions(tenantManager, ignoreCrossTenantSubscriptions);
                migrateFrom310.registryResourceMigration();
                migrateFrom310.scopeMigration();
                migrateFrom310.spMigration();
            } else {
                MigrationClientFactory.initFactory(tenants, blackListTenants, tenantRange, registryService, tenantManager,
                        removeDecryptionFailedKeysFromDB);

                MigrationExecutor.Arguments arguments = new MigrationExecutor.Arguments();
                arguments.setMigrateFromVersion(migrateFromVersion);
                arguments.setSpecificVersion(specificVersion);
                arguments.setComponent(component);
                arguments.setMigrateAll(migrateAll);
                arguments.setCleanupNeeded(cleanupNeeded);
                arguments.setDBMigration(isDBMigration);
                arguments.setRegistryMigration(isRegistryMigration);
                arguments.setFileSystemMigration(isFileSystemMigration);
                arguments.setTriggerAPIIndexer(isTriggerAPIIndexer);
                arguments.setStatMigration(isStatMigration);
                arguments.setOptions(options);
                arguments.setSP_APP_Migration(isSP_APP_Population);
                MigrationExecutor.execute(arguments);
            }
        } catch (APIMigrationException e) {
            log.error("API Management  exception occurred while migrating", e);
        } catch (UserStoreException e) {
            log.error("User store  exception occurred while migrating", e);
        } catch (SQLException e) {
            log.error("SQL exception occurred while migrating", e);
        } catch (Exception e) {
            log.error("Generic exception occurred while migrating", e);
        } catch (Throwable t) {
            log.error("Throwable error", t);
        } finally {
            MigrationClientFactory.clearFactory();
        }
        log.info("WSO2 API Manager migration component successfully activated.");
    }
}
