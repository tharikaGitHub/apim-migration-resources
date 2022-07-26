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
import org.wso2.carbon.apimgt.migration.client.*;
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
    private final String V320 = "3.2.0";

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
        log.info("Starting APIM Migration from APIM " + migrateFromVersion + " ..............");

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

        try {
            RegistryServiceImpl registryService = new RegistryServiceImpl();
            TenantManager tenantManager = ServiceHolder.getRealmService().getTenantManager();
            MigrateUUIDToDB commonMigrationClient = new MigrateUUIDToDB(tenants, blackListTenants, tenantRange,
                    tenantManager);
            IdentityScopeMigration identityScopeMigration = new IdentityScopeMigration();
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
                log.info("Start Migrating WSO2 API Manager " + migrateFromVersion +" registry resources ..........");
                migrateFrom200.registryResourceMigration();
                log.info("Successfully migrated WSO2 API Manager " + migrateFromVersion +" registry resources.");


                MigrationClient scopeRoleMappingPopulation = new ScopeRoleMappingPopulationClient(tenants, blackListTenants, tenantRange, registryService, tenantManager);

                log.info("Populating WSO2 API Manager Scope-Role Mapping from APIM " + migrateFromVersion +
                        " ..........");
                scopeRoleMappingPopulation.updateScopeRoleMappings();
                log.info("Successfully updated the Scope Role Mappings..........");
                scopeRoleMappingPopulation.populateScopeRoleMapping();
                log.info("Successfully populated the Scope Role Mappings ..........");

                MigrationClient migrateFrom310 = new MigrateFrom310(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                log.info("Start scope migration from APIM 3.1.0 ..........");
                migrateFrom310.scopeMigration();
                log.info("Successfully migrated the Scopes from APIM 3.1.0 ..........");

                log.info("Start SP migration from APIM 3.1.0 ..........");
                migrateFrom310.spMigration();
                log.info("Successfully migrated the SP from APIM 3.1.0 ..........");

                log.info("Start API Property visibility migration from APIM 3.1.0 ..........");
                migrateFrom310.updateAPIPropertyVisibility();
                log.info("Successfully migrated the API Properties for visibility");

                migrateFrom310.updateAPITypeInDB();

                log.info("Starting Migration from API Manager 3.2 to 4.0");
                log.info("Start moving UUIDs to DB from registry ..........");
                commonMigrationClient.moveUUIDToDBFromRegistry();
                log.info("Successfully moved the UUIDs to DB from registry ..........");

                log.info("Start identity scope migration ..........");
                identityScopeMigration.migrateScopes();
                log.info("Successfully migrated the identity scopes. ");
                MigrateFrom320 migrateFrom320 = new MigrateFrom320(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                log.info("Start migrating WebSocket APIs ..........");
                migrateFrom320.migrateWebSocketAPI();
                log.info("Successfully migrated WebSocket APIs ..........");

                log.info("Start migrating API Product Mappings  ..........");
                migrateFrom320.migrateProductMappingTable();
                log.info("Successfully migrated API Product Mappings ..........");

                log.info("Start migrating registry paths of Icon and WSDLs  ..........");
                migrateFrom320.updateRegistryPathsOfIconAndWSDL();
                log.info("Successfully migrated API registry paths of Icon and WSDLs.");

                log.info("Start removing unnecessary fault handlers from fault sequences ..........");
                migrateFrom320.removeUnnecessaryFaultHandlers();
                log.info("Successfully removed the unnecessary fault handlers from fault sequences.");

                log.info("Start API Revision related migration ..........");
                migrateFrom320.apiRevisionRelatedMigration();
                log.info("API Revision related migration is successful.");

                log.info("Start migrating Endpoint Certificates  ..........");
                migrateFrom320.migrateEndpointCertificates();
                log.info("Successfully migrated Endpoint Certificates.");

                log.info("Start replacing KM name by UUID  ..........");
                migrateFrom320.replaceKMNamebyUUID();
                log.info("Successfully replaced KM name by UUID.");

                log.info("Migrated Successfully to APIM 4.0.0");

            } else if (V210.equals(migrateFromVersion) || V220.equals(migrateFromVersion) ||
                    V250.equals(migrateFromVersion) || V260.equals(migrateFromVersion)) {
                log.info("Start migration from APIM " + migrateFromVersion + "  ..........");

                MigrationClient migrateFrom210 = new MigrateFrom210(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Migrating WSO2 API Manager registry resources ..........");
                migrateFrom210.registryResourceMigration();
                log.info("Successfully migrated registry resources .");

                MigrationClient scopeRoleMappingPopulation = new ScopeRoleMappingPopulationClient(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Populating WSO2 API Manager Scope-Role Mapping to migrate from APIM " + migrateFromVersion);
                scopeRoleMappingPopulation.updateScopeRoleMappings();
                log.info("Successfully updated the Scope Role Mappings ..........");
                scopeRoleMappingPopulation.populateScopeRoleMapping();
                log.info("Successfully populated the Scope Role Mappings ..........");

                log.info("Migrated Successfully to API Manager 3.1");
                log.info("Starting Migration from API Manager 3.1 to 3.2");

                MigrationClient migrateFrom310 = new MigrateFrom310(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                migrateFrom310.scopeMigration();
                log.info("Successfully migrated the Scopes from APIM " + migrateFromVersion);

                migrateFrom310.spMigration();
                log.info("Successfully migrated the SPs from APIM " + migrateFromVersion);

                migrateFrom310.updateAPIPropertyVisibility();
                log.info("Successfully migrated the API Properties for visibility from APIM " + migrateFromVersion);

                migrateFrom310.updateAPITypeInDB();

                log.info("Migrated Successfully to APIM 3.2.0 ");

                log.info("Starting Migration from API Manager 3.2.0 to 4.0.0 .................");
                log.info("Start moving UUIDs to DB from registry ..........");
                commonMigrationClient.moveUUIDToDBFromRegistry();
                log.info("Successfully moved the UUIDs to DB from registry ..........");

                log.info("Start identity scope migration ..........");
                identityScopeMigration.migrateScopes();
                log.info("Successfully migrated the identity scopes. ");

                MigrateFrom320 migrateFrom320 = new MigrateFrom320(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);

                log.info("Start migrating WebSocket APIs ..........");
                migrateFrom320.migrateWebSocketAPI();
                log.info("Successfully migrated WebSocket APIs ..........");

                if (V250.equals(migrateFromVersion) || V260.equals(migrateFromVersion)) {
                    log.info("Migrating lables to vhosts in APIM " + migrateFromVersion);
                    migrateFrom320.migrateLabelsToVhosts();
                    log.info("Successfully Migrated lables to vhosts in APIM " + migrateFromVersion);
                }
                log.info("Start migrating API Product Mappings  ..........");
                migrateFrom320.migrateProductMappingTable();
                log.info("Successfully migrated API Product Mappings ..........");

                log.info("Start migrating registry paths of Icon and WSDLs  ..........");
                migrateFrom320.updateRegistryPathsOfIconAndWSDL();
                log.info("Successfully migrated API registry paths of Icon and WSDLs.");

                log.info("Start removing unnecessary fault handlers from fault sequences ..........");
                migrateFrom320.removeUnnecessaryFaultHandlers();
                log.info("Successfully removed the unnecessary fault handlers from fault sequences.");

                log.info("Start API Revision related migration ..........");
                migrateFrom320.apiRevisionRelatedMigration();
                log.info("API Revision related migration is successful.");

                log.info("Start migrating Endpoint Certificates  ..........");
                migrateFrom320.migrateEndpointCertificates();
                log.info("Successfully migrated Endpoint Certificates.");

                log.info("Start replacing KM name by UUID  ..........");
                migrateFrom320.replaceKMNamebyUUID();
                log.info("Successfully replaced KM name by UUID.");

                log.info("Migrated Successfully to 4.0.0");
            } else if (isScopeRoleMappingPopulation) {
                MigrationClient scopeRoleMappingPopulation = new ScopeRoleMappingPopulationClient(tenants, blackListTenants, tenantRange, registryService, tenantManager);
                log.info("Populating WSO2 API Manager Scope-Role Mapping");
                scopeRoleMappingPopulation.populateScopeRoleMapping();
            } else if(V310.equals(migrateFromVersion) || V300.equals(migrateFromVersion)) {
                MigrationClient migrateFrom310 = new MigrateFrom310(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                migrateFrom310.registryResourceMigration();
                migrateFrom310.scopeMigration();
                migrateFrom310.spMigration();
                migrateFrom310.updateAPIPropertyVisibility();
                migrateFrom310.updateAPITypeInDB();
                log.info("Migrated Successfully to 3.2");
                log.info("Starting Migration from API Manager 3.2 to 4.0");

                log.info("Start moving UUIDs to DB from registry ..........");
                commonMigrationClient.moveUUIDToDBFromRegistry();
                log.info("Successfully moved the UUIDs to DB from registry ..........");

                log.info("Start identity scope migration ..........");
                identityScopeMigration.migrateScopes();
                log.info("Successfully migrated the identity scopes. ");

                MigrateFrom320 migrateFrom320 = new MigrateFrom320(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);
                log.info("Start migrating WebSocket APIs ..........");
                migrateFrom320.migrateWebSocketAPI();
                log.info("Successfully migrated WebSocket APIs ..........");

                migrateFrom320.migrateLabelsToVhosts();
                log.info("Start migrating API Product Mappings  ..........");
                migrateFrom320.migrateProductMappingTable();
                log.info("Successfully migrated API Product Mappings ..........");

                log.info("Start migrating registry paths of Icon and WSDLs  ..........");
                migrateFrom320.updateRegistryPathsOfIconAndWSDL();
                log.info("Successfully migrated API registry paths of Icon and WSDLs.");

                log.info("Start removing unnecessary fault handlers from fault sequences ..........");
                migrateFrom320.removeUnnecessaryFaultHandlers();
                log.info("Successfully removed the unnecessary fault handlers from fault sequences.");

                log.info("Start API Revision related migration ..........");
                migrateFrom320.apiRevisionRelatedMigration();
                log.info("Successfully done the API Revision related migration.");

                log.info("Start migrating Endpoint Certificates  ..........");
                migrateFrom320.migrateEndpointCertificates();
                log.info("Successfully migrated Endpoint Certificates.");

                log.info("Start replacing KM name by UUID  ..........");
                migrateFrom320.replaceKMNamebyUUID();
                log.info("Successfully replaced KM name by UUID.");

                log.info("Migrated Successfully to 4.0.0");
            } else if (V320.equals(migrateFromVersion)) {
                commonMigrationClient.moveUUIDToDBFromRegistry();
                MigrateFrom320 migrateFrom320 = new MigrateFrom320(tenants, blackListTenants,
                        tenantRange, registryService, tenantManager);

                log.info("Start migrating WebSocket APIs ..........");
                migrateFrom320.migrateWebSocketAPI();
                log.info("Successfully migrated WebSocket APIs ..........");

                migrateFrom320.migrateLabelsToVhosts();
                log.info("Start migrating API Product Mappings  ..........");
                migrateFrom320.migrateProductMappingTable();
                log.info("Successfully migrated API Product Mappings ..........");

                log.info("Start migrating registry paths of Icon and WSDLs  ..........");
                migrateFrom320.updateRegistryPathsOfIconAndWSDL();
                log.info("Successfully migrated API registry paths of Icon and WSDLs.");

                log.info("Start removing unnecessary fault handlers from fault sequences ..........");
                migrateFrom320.removeUnnecessaryFaultHandlers();
                log.info("Successfully removed the unnecessary fault handlers from fault sequences.");

                log.info("Start API Revision related migration ..........");
                migrateFrom320.apiRevisionRelatedMigration();
                log.info("Successfully done the API Revision related migration.");

                log.info("Start migrating Endpoint Certificates  ..........");
                migrateFrom320.migrateEndpointCertificates();
                log.info("Successfully migrated Endpoint Certificates.");

                log.info("Start replacing KM name by UUID  ..........");
                migrateFrom320.replaceKMNamebyUUID();
                log.info("Successfully replaced KM name by UUID.");

                log.info("Migrated Successfully to 4.0.0");

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
