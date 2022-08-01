/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.apimgt.migration.client.sp_migration;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Arrays;


public class DBManagerImpl implements DBManager {

    private static final Log log = LogFactory.getLog(DBManagerImpl.class);

    private static volatile DataSource oldStatsDataSource = null;
    private static volatile DataSource newStatsDataSource = null;
    private static volatile DataSource apimDataSource = null;
    private static String OLD_STATS_DATA_SOURCE_NAME = "jdbc/WSO2AM_STATS_DB";
    private static String NEW_STATS_DATA_SOURCE_NAME = "jdbc/APIM_ANALYTICS_DB";
    private static String APIM_DATA_SOURCE_NAME = "jdbc/WSO2AM_DB";
    private static final String TENANT_DOMAIN_IDENTIFIER = "/t/";
    private static final String COLON_IDENTIFIER = ":";
    private static final char AT_IDENTIFIER = '@';
    private static final String DASH_IDENTIFIER = "-";
    private static final String SUPER_TENANT_DOMAIN = "carbon.super";
    private static final String ANONYMOUS_USER = "anonymous";
    private static final String UNAUTHENTICATED_API_IDENTIFIER = "None";

    /**
     * This method initializes the datasources required for the migration of the stats dbs
     *
     * @throws APIMStatMigrationException when there is an error looking up the datasources
     */
    @Override
    public void initialize(String migrateFromVersion) throws APIMStatMigrationException {
        String dataSourceName = System.getProperty(APIMStatMigrationConstants.DATA_SOURCE_NAME);
        String[] dataSourceNames;
        //Get data source as outside parameter
        if (dataSourceName != null) {
            dataSourceNames = dataSourceName.split(",");
            APIM_DATA_SOURCE_NAME = dataSourceNames[0];
            OLD_STATS_DATA_SOURCE_NAME = dataSourceNames[1];
            NEW_STATS_DATA_SOURCE_NAME = dataSourceNames[2];
        }

        if(migrateFromVersion.equals("2.0.0") || migrateFromVersion.equals("2.1.0") ||
                migrateFromVersion.equals("2.2.0") || migrateFromVersion.equals("2.5.0")) {
            try {
                Context ctx = new InitialContext();
                oldStatsDataSource = (DataSource) ctx.lookup(OLD_STATS_DATA_SOURCE_NAME);
            } catch (NamingException e) {
                String msg = "Error while looking up the data source: " + OLD_STATS_DATA_SOURCE_NAME;
                log.error(msg);
                throw new APIMStatMigrationException(msg);
            }
        }

        try {
            Context ctx = new InitialContext();
            newStatsDataSource = (DataSource) ctx.lookup(NEW_STATS_DATA_SOURCE_NAME);
        } catch (NamingException e) {
            String msg = "Error while looking up the data source: " + NEW_STATS_DATA_SOURCE_NAME;
            log.error(msg);
            throw new APIMStatMigrationException(msg);
        }

        try {
            Context ctx = new InitialContext();
            apimDataSource = (DataSource) ctx.lookup(APIM_DATA_SOURCE_NAME);
        } catch (NamingException e) {
            String msg = "Error while looking up the data source: " + APIM_DATA_SOURCE_NAME;
            log.error(msg);
            throw new APIMStatMigrationException(msg);
        }
    }

    /**
     * This method migrates the data related to the API_DESTINATION_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateDestinationSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_DESTINATION_SUMMARY;
            String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_PER_DESTINATION_AGG
                    + "_DAYS(apiName, apiVersion, apiCreator, apiContext, destination, AGG_COUNT, apiHostname, "
                    + "AGG_TIMESTAMP, AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, gatewayType, label, regionalID, " +
                    "apiCreatorTenantDomain) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,'SYNAPSE','Synapse','default',?)";
            statement1 = con1.prepareStatement(retrieveQuery);
            statement2 = con2.prepareStatement(insertQuery);
            resultSetRetrieved = statement1.executeQuery();
            while (resultSetRetrieved.next()) {
                String api = resultSetRetrieved.getString("api");
                String version = resultSetRetrieved.getString("version");
                String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                String context = resultSetRetrieved.getString("context");
                String destination = resultSetRetrieved.getString("destination");
                String apiCreatorTenantDomain = null;
                long total_request_count = resultSetRetrieved.getLong("total_request_count");
                String hostName = resultSetRetrieved.getString("hostName");
                int year = resultSetRetrieved.getInt("year");
                int month = resultSetRetrieved.getInt("month");
                int day = resultSetRetrieved.getInt("day");
                String time = resultSetRetrieved.getString("time");
                //Get apiCreatorTenantDomain from Context
                if (context.contains(TENANT_DOMAIN_IDENTIFIER)) {
                    apiCreatorTenantDomain = apiPublisher.substring(apiPublisher.lastIndexOf(AT_IDENTIFIER) + 1,
                            apiPublisher.length());
                } else {
                    apiCreatorTenantDomain = SUPER_TENANT_DOMAIN;
                }
                statement2.setString(1, api);
                statement2.setString(2, version);
                statement2.setString(3, apiPublisher);
                statement2.setString(4, context);
                statement2.setString(5, destination);
                statement2.setLong(6, total_request_count);
                statement2.setString(7, hostName);
                String dayInString = year + "-" + month + "-" + day;
                statement2.setLong(8, getTimestampOfDay(dayInString));
                statement2.setLong(9, getTimestamp(time));
                //Same Value AGG_EVENT_TIMESTAMP
                statement2.setLong(10, getTimestamp(time));
                statement2.setString(11, apiCreatorTenantDomain);
                statement2.executeUpdate();
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_RESOURCE_USAGE_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateResourceUsageSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        Connection con3 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        PreparedStatement statement3 = null;
        PreparedStatement statement4 = null;
        PreparedStatement statement5 = null;
        ResultSet resultSetRetrieved = null;
        ResultSet ifExistresultSetRetrieved = null;
        ResultSet resultSetFromAMDB = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            con3 = apimDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_RESOURCE_USAGE_SUMMARY, con1)) { //Tables exist
                String ifExistQuery = "Select * from " + APIMStatMigrationConstants.API_RESOURCE_PATH_AGG
                        + "_DAYS where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                        "AND apiName=? AND apiVersion=?";
                String consumerKeyMappingQuery = "select APPLICATION_ID from AM_APPLICATION_KEY_MAPPING " +
                        "WHERE CONSUMER_KEY=?";
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_RESOURCE_USAGE_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_RESOURCE_PATH_AGG
                        + "_DAYS(apiName, apiVersion, apiCreator, apiResourceTemplate, apiContext, apiMethod, AGG_COUNT, "
                        + "apiHostname, AGG_TIMESTAMP, AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, applicationId, "
                        + "gatewayType, label, regionalID, applicationName, apiCreatorTenantDomain) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'SYNAPSE','Synapse','default',?,?)";
                String updateQuery = "Update " + APIMStatMigrationConstants.API_RESOURCE_PATH_AGG
                        + "_DAYS Set AGG_COUNT=AGG_COUNT+? where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                        "AND apiName=? AND apiVersion=?";
                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                statement3 = con3.prepareStatement(consumerKeyMappingQuery);
                statement4 = con2.prepareStatement(ifExistQuery);
                statement5 = con2.prepareStatement(updateQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String version = resultSetRetrieved.getString("version");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    String consumerKey = resultSetRetrieved.getString("consumerKey");
                    String applicationName = null, apiCreatorTenantDomain = null;
                    int applicationId = -1;
                    // Unauthenticated API, Application Id set to 0.
                    if (consumerKey == null || consumerKey.equalsIgnoreCase(DASH_IDENTIFIER)) {
                        applicationId = 0;
                    } else {
                        statement3.setString(1, consumerKey);
                        resultSetFromAMDB = statement3.executeQuery();
                        //ConsumerKey not in the AM_APPLICATION_KEY_MAPPING table.
                        if (!resultSetFromAMDB.next()) {
                            // Remove from the migrate data
                            log.info("WSO2 API-M Migration Task : ConsumerKey " + consumerKey + " Does not contain "
                                    + "in the AM_APPLICATION_KEY_MAPPING table.");
                            continue;
                        } else {
                            applicationId = resultSetFromAMDB.getInt("APPLICATION_ID");
                        }
                    }
                    //Set Application name to "None" for Unauthenticated APIs.
                    if (applicationId == 0) {
                        applicationName = UNAUTHENTICATED_API_IDENTIFIER;
                    } else {
                        applicationName = getApplicationNameByAppID(applicationId);
                    }
                    String resourcePath = resultSetRetrieved.getString("resourcePath");
                    if (resourcePath.length() >= 255) {
                        resourcePath = resourcePath.substring(0, 253);
                    }
                    String context = resultSetRetrieved.getString("context");
                    //Get apiCreatorTenantDomain from Context
                    if (context.contains(TENANT_DOMAIN_IDENTIFIER)) {
                        apiCreatorTenantDomain = apiPublisher.substring(apiPublisher.lastIndexOf(AT_IDENTIFIER) + 1,
                                apiPublisher.length() - 1);
                    } else {
                        apiCreatorTenantDomain = SUPER_TENANT_DOMAIN;
                    }

                    String method = resultSetRetrieved.getString("method");
                    long total_request_count = resultSetRetrieved.getLong("total_request_count");
                    String hostName = resultSetRetrieved.getString("hostName");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    String time = resultSetRetrieved.getString("time");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, apiPublisher);
                    statement2.setString(4, resourcePath);
                    statement2.setString(5, context);
                    statement2.setString(6, method);
                    statement2.setLong(7, total_request_count);
                    statement2.setString(8, hostName);
                    String dayInString = year + "-" + month + "-" + day;
                    statement2.setLong(9, getTimestampOfDay(dayInString));
                    statement2.setLong(10, getTimestamp(time));
                    statement2.setLong(11, getTimestamp(time));
                    if (applicationId != -1) {
                        statement2.setString(12, Integer.toString(applicationId));
                    } else {
                        String errorMsg = "Error occurred while retrieving applicationId for consumer key : " + consumerKey;
                        log.error(errorMsg);
                        throw new APIMStatMigrationException(errorMsg);
                    }
                    statement2.setString(13, applicationName);
                    statement2.setString(14, apiCreatorTenantDomain);

                    statement4.setLong(1, getTimestampOfDay(dayInString));
                    statement4.setLong(2, getTimestamp(time));
                    statement4.setString(3, Integer.toString(applicationId));
                    statement4.setString(4, context);
                    statement4.setString(5, api);
                    statement4.setString(6, version);
                    ifExistresultSetRetrieved = statement4.executeQuery();
                    //Check if result already available or not
                    if (!ifExistresultSetRetrieved.next()) {
                        statement2.executeUpdate();
                    } else {
                        statement5.setLong(1, total_request_count);
                        statement5.setLong(2, getTimestampOfDay(dayInString));
                        statement5.setLong(3, getTimestamp(time));
                        statement5.setString(4, Integer.toString(applicationId));
                        statement5.setString(5, context);
                        statement5.setString(6, api);
                        statement5.setString(7, version);
                        statement5.executeUpdate();
                    }
                }
            } else {
                String msg = APIMStatMigrationConstants.API_RESOURCE_USAGE_SUMMARY + " Table does not exists.";
                log.error(msg);
                return;
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
            closeDatabaseLinks(resultSetFromAMDB, statement3, con3);
        }
    }

    /**
     * This method migrates the data related to the API_VERSION_USAGE_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateVersionUsageSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        Connection con3 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        PreparedStatement statement3 = null;
        PreparedStatement statement4 = null;
        PreparedStatement statement5 = null;
        ResultSet resultSetRetrieved = null;
        ResultSet ifExistresultSetRetrieved = null;
        ResultSet resultSetFromAMDB = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            con3 = apimDataSource.getConnection();
            String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_REQUEST_SUMMARY;
            String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_VERSION_USAGE_AGG
                    + "_DAYS(apiName, apiVersion, apiCreator, apiContext, AGG_COUNT, apiHostname, AGG_TIMESTAMP, "
                    + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, applicationId, applicationName, apiCreatorTenantDomain, gatewayType, label, regionalID) "
                    + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'SYNAPSE','Synapse','default')";
            String consumerKeyMappingQuery = "select APPLICATION_ID from " +
                    "AM_APPLICATION_KEY_MAPPING WHERE CONSUMER_KEY=?";

            String ifExistQuery = "Select * from " + APIMStatMigrationConstants.API_VERSION_USAGE_AGG
                    + "_DAYS where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                    "AND apiName=? AND apiVersion=?";

            String updateQuery = "Update " + APIMStatMigrationConstants.API_VERSION_USAGE_AGG
                    + "_DAYS Set AGG_COUNT=AGG_COUNT+? where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                    "AND apiName=? AND apiVersion=?";

            statement1 = con1.prepareStatement(retrieveQuery);
            statement2 = con2.prepareStatement(insertQuery);
            statement3 = con3.prepareStatement(consumerKeyMappingQuery);
            statement4 = con2.prepareStatement(ifExistQuery);
            statement5 = con2.prepareStatement(updateQuery);
            resultSetRetrieved = statement1.executeQuery();
            while (resultSetRetrieved.next()) {
                String api = resultSetRetrieved.getString("api");
                String version = resultSetRetrieved.getString("version");
                String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                String context = resultSetRetrieved.getString("context");
                long total_request_count = resultSetRetrieved.getLong("total_request_count");
                String hostName = resultSetRetrieved.getString("hostName");
                int year = resultSetRetrieved.getInt("year");
                int month = resultSetRetrieved.getInt("month");
                int day = resultSetRetrieved.getInt("day");
                String time = resultSetRetrieved.getString("time");
                String consumerKey = resultSetRetrieved.getString("consumerKey");
                String apiCreatorTenantDomain = null;
                String applicationName = null;
                // Unauthenticated API, Application Id set to 0.
                int applicationId = -1;
                if (consumerKey == null || consumerKey.equalsIgnoreCase(DASH_IDENTIFIER)) {
                    applicationId = 0;
                } else {
                    statement3.setString(1, consumerKey);
                    resultSetFromAMDB = statement3.executeQuery();
                    //ConsumerKey not in the AM_APPLICATION_KEY_MAPPING table.
                    if (!resultSetFromAMDB.next()) {
                        // Remove from the migrate data
                        log.info("WSO2 API-M Migration Task : ConsumerKey " + consumerKey + " Does not contain in "
                                + "the AM_APPLICATION_KEY_MAPPING table.");
                        continue;
                    } else {
                        applicationId = resultSetFromAMDB.getInt("APPLICATION_ID");
                    }
                }

                //Set Application name to "None" for Unauthenticated APIs.
                if (applicationId == 0) {
                    applicationName = UNAUTHENTICATED_API_IDENTIFIER;
                } else {
                    applicationName = getApplicationNameByAppID(applicationId);
                }

                if (context.contains(TENANT_DOMAIN_IDENTIFIER)) {
                    apiCreatorTenantDomain = apiPublisher.substring(apiPublisher.lastIndexOf(AT_IDENTIFIER) + 1,
                            apiPublisher.length());
                } else {
                    apiCreatorTenantDomain = SUPER_TENANT_DOMAIN;
                }

                statement2.setString(1, api);
                statement2.setString(2, version);
                statement2.setString(3, apiPublisher);
                statement2.setString(4, context);
                statement2.setLong(5, total_request_count);
                statement2.setString(6, hostName);
                String dayInString = year + "-" + month + "-" + day;
                statement2.setLong(7, getTimestampOfDay(dayInString));
                statement2.setLong(8, getTimestamp(time));
                statement2.setLong(9, getTimestamp(time)); //same as AGG_EVENT_TIMESTAMP
                statement2.setInt(10, applicationId);
                statement2.setString(11, applicationName);
                statement2.setString(12,apiCreatorTenantDomain);


                statement4.setLong(1, getTimestampOfDay(dayInString));
                statement4.setLong(2, getTimestamp(time));
                statement4.setString(3, Integer.toString(applicationId));
                statement4.setString(4, context);
                statement4.setString(5, api);
                statement4.setString(6, version);
                ifExistresultSetRetrieved = statement4.executeQuery();
                //Check if result already available or not
                if (!ifExistresultSetRetrieved.next()) {
                    statement2.executeUpdate();
                } else {
                    statement5.setLong(1, total_request_count);
                    statement5.setLong(2, getTimestampOfDay(dayInString));
                    statement5.setLong(3, getTimestamp(time));
                    statement5.setString(4, Integer.toString(applicationId));
                    statement5.setString(5, context);
                    statement5.setString(6, api);
                    statement5.setString(7, version);
                    statement5.executeUpdate();
                }
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
            closeDatabaseLinks(resultSetFromAMDB, statement3, con3);
        }
    }

    /**
     * This method migrates the data related to the API_LAST_ACCESS_TIME_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateLastAccessTimeSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_LAST_ACCESS_TIME_SUMMARY;
            String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_LAST_ACCESS_SUMMARY_AGG
                    + "(apiCreatorTenantDomain, apiCreator, apiName, apiVersion, applicationOwner, apiContext, " +
                    "lastAccessTime) VALUES(?,?,?,?,?,?,?)";
            statement1 = con1.prepareStatement(retrieveQuery);
            statement2 = con2.prepareStatement(insertQuery);
            resultSetRetrieved = statement1.executeQuery();
            while (resultSetRetrieved.next()) {
                String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                String api = resultSetRetrieved.getString("api");
                String version = resultSetRetrieved.getString("version");
                String userId = resultSetRetrieved.getString("userId");
                String context = resultSetRetrieved.getString("context");
                long max_request_time = resultSetRetrieved.getLong("max_request_time");
                statement2.setString(1, tenantDomain);
                statement2.setString(2, apiPublisher);
                statement2.setString(3, api);
                statement2.setString(4, version);
                statement2.setString(5, userId);
                statement2.setString(6, context);
                statement2.setLong(7, max_request_time);
                statement2.executeUpdate();
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_FAULT_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateFaultSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        Connection con3 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        PreparedStatement statement3 = null;
        PreparedStatement statement4 = null;
        PreparedStatement statement5 = null;
        ResultSet ifExistresultSetRetrieved = null;
        ResultSet resultSetRetrieved = null;
        ResultSet resultSetFromAMDB = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            con3 = apimDataSource.getConnection();
            String consumerKeyMappingQuery = "SELECT APPLICATION_ID FROM AM_APPLICATION_KEY_MAPPING " +
                    "WHERE CONSUMER_KEY=?";
            String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_FAULT_SUMMARY;
            String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_FAULTY_INVOCATION_AGG
                    + "_DAYS(apiName, apiVersion, apiCreator, applicationId, apiContext, AGG_COUNT, hostname, "
                    + "AGG_TIMESTAMP, AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, applicationName, apiCreatorTenantDomain, regionalID) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'default')";
            String ifExistQuery = "Select * from " + APIMStatMigrationConstants.API_FAULTY_INVOCATION_AGG
                    + "_DAYS where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                    "AND apiName=? AND apiVersion=?";
            String updateQuery = "Update " + APIMStatMigrationConstants.API_FAULTY_INVOCATION_AGG
                    + "_DAYS Set AGG_COUNT=AGG_COUNT+? where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                    "AND apiName=? AND apiVersion=?";
            statement1 = con1.prepareStatement(retrieveQuery);
            statement2 = con2.prepareStatement(insertQuery);
            statement3 = con3.prepareStatement(consumerKeyMappingQuery);
            statement4 = con2.prepareStatement(ifExistQuery);
            statement5 = con2.prepareStatement(updateQuery);
            resultSetRetrieved = statement1.executeQuery();
            while (resultSetRetrieved.next()) {
                String api = resultSetRetrieved.getString("api");
                String version = resultSetRetrieved.getString("version");
                String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                String consumerKey = resultSetRetrieved.getString("consumerKey");
                statement3.setString(1, consumerKey);
                resultSetFromAMDB = statement3.executeQuery();
                String apiCreatorTenantDomain = null;
                String applicationName = null;
                int applicationId = -1;
                if (consumerKey == null || consumerKey.equalsIgnoreCase(DASH_IDENTIFIER)) {
                    applicationId = 0;
                } else {
                    statement3.setString(1, consumerKey);
                    resultSetFromAMDB = statement3.executeQuery();
                    //ConsumerKey not in the AM_APPLICATION_KEY_MAPPING table.
                    if (!resultSetFromAMDB.next()) {
                        // Remove from the migrate data
                        log.info("WSO2 API-M Migration Task : ConsumerKey " + consumerKey + " Does not contain in "
                                + "the AM_APPLICATION_KEY_MAPPING table.");
                        continue;
                    } else {
                        applicationId = resultSetFromAMDB.getInt("APPLICATION_ID");
                    }
                }
                String context = resultSetRetrieved.getString("context");
                long total_fault_count = resultSetRetrieved.getLong("total_fault_count");
                String hostName = resultSetRetrieved.getString("hostName");
                int year = resultSetRetrieved.getInt("year");
                int month = resultSetRetrieved.getInt("month");
                int day = resultSetRetrieved.getInt("day");
                String time = resultSetRetrieved.getString("time");
                statement2.setString(1, api);
                statement2.setString(2, version);
                statement2.setString(3, apiPublisher);
                if (applicationId != -1) {
                    statement2.setString(4, Integer.toString(applicationId));
                } else {
                    String errorMsg = "Error occurred while retrieving applicationId for consumer key : " + consumerKey;
                    log.error(errorMsg);
                    throw new APIMStatMigrationException(errorMsg);
                }

                //Set Application name to "None" for Unauthenticated APIs.
                if (applicationId == 0) {
                    applicationName = UNAUTHENTICATED_API_IDENTIFIER;
                } else {
                    applicationName = getApplicationNameByAppID(applicationId);
                }

                if (context.contains(TENANT_DOMAIN_IDENTIFIER)) {
                    apiCreatorTenantDomain = apiPublisher.substring(apiPublisher.lastIndexOf(AT_IDENTIFIER) + 1,
                            apiPublisher.length());
                } else {
                    apiCreatorTenantDomain = SUPER_TENANT_DOMAIN;
                }

                statement2.setString(5, context);
                statement2.setLong(6, total_fault_count);
                statement2.setString(7, hostName);
                String dayInString = year + "-" + month + "-" + day;
                statement2.setLong(8, getTimestampOfDay(dayInString));
                statement2.setLong(9, getTimestamp(time));
                statement2.setLong(10, getTimestamp(time));
                statement2.setString(11, applicationName);
                statement2.setString(12,apiCreatorTenantDomain);

                statement4.setLong(1, getTimestampOfDay(dayInString));
                statement4.setLong(2, getTimestamp(time));
                statement4.setString(3, Integer.toString(applicationId));
                statement4.setString(4, context);
                statement4.setString(5, api);
                statement4.setString(6, version);
                ifExistresultSetRetrieved = statement4.executeQuery();
                //Check if result already available or not
                if (!ifExistresultSetRetrieved.next()) {
                    statement2.executeUpdate();
                } else {
                    statement5.setLong(1, total_fault_count);
                    statement5.setLong(2, getTimestampOfDay(dayInString));
                    statement5.setLong(3, getTimestamp(time));
                    statement5.setString(4, Integer.toString(applicationId));
                    statement5.setString(5, context);
                    statement5.setString(6, api);
                    statement5.setString(7, version);
                    statement5.executeUpdate();
                }
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
            closeDatabaseLinks(resultSetFromAMDB, statement3, con3);
        }
    }

    /**
     * This method migrates the data related to the API_REQ_USR_BROW_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateUserBrowserSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_REQ_USR_BROW_SUMMARY, con1)) { //Tables exist
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_REQ_USR_BROW_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_USER_BROWSER_AGG
                        + "_DAYS(apiName, apiVersion, apiCreator, apiCreatorTenantDomain, AGG_COUNT, AGG_TIMESTAMP, "
                        + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, operatingSystem, browser, apiContext, gatewayType,"
                        + " label, regionalID) VALUES(?,?,?,?,?,?,?,?,?,?,?,'SYNAPSE','Synapse','default')";
                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String version = resultSetRetrieved.getString("version");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                    String context = getContextByAPI(api, version, apiPublisher);
                    //API does not contain in AM_API table
                    if (context == null) {
                        continue;
                    }
                    long total_request_count = resultSetRetrieved.getLong("total_request_count");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    long time = resultSetRetrieved.getLong("requestTime");
                    String os = resultSetRetrieved.getString("os");
                    String browser = resultSetRetrieved.getString("browser");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, apiPublisher);
                    statement2.setString(4, tenantDomain);
                    statement2.setLong(5, total_request_count);
                    String dayInString = year + "-" + month + "-" + day;
                    statement2.setLong(6, getTimestampOfDay(dayInString));
                    statement2.setLong(7, time);
                    statement2.setLong(8, time);
                    statement2.setString(9, os);
                    statement2.setString(10, browser);
                    statement2.setString(11, context);
                    statement2.executeUpdate();
                }
            } else {
                String msg = APIMStatMigrationConstants.API_REQ_USR_BROW_SUMMARY + " Table does not exists.";
                log.error(msg);
                return;
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_EXE_TME_DAY_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateExecutionTimeDaySummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_EXE_TME_DAY_SUMMARY, con1)) { //Tables exist
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_EXE_TME_DAY_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_EXEC_TIME_AGG
                        + "_DAYS(apiName, apiVersion, apiCreatorTenantDomain, apiCreator, AGG_SUM_responseTime, apiContext, "
                        + "AGG_SUM_securityLatency, AGG_SUM_throttlingLatency, AGG_SUM_requestMedLat, "
                        + "AGG_SUM_responseMedLat, AGG_SUM_backendLatency, AGG_SUM_otherLatency, AGG_TIMESTAMP, "
                        + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, apiHostname, apiResourceTemplate, apiMethod, "
                        + "regionalID, AGG_COUNT) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'default',1)";
                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String version = resultSetRetrieved.getString("version");
                    String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    if (apiPublisher.contains(SUPER_TENANT_DOMAIN)) {
                        apiPublisher = apiPublisher.substring(0, apiPublisher.lastIndexOf("@"));
                    }
                    String[] result = getResourceByAPI(api, version, apiPublisher);
                    long apiResponseTime = resultSetRetrieved.getLong("apiResponseTime");
                    String context = resultSetRetrieved.getString("context");
                    long securityLatency = resultSetRetrieved.getLong("securityLatency");
                    long throttlingLatency = resultSetRetrieved.getLong("throttlingLatency");
                    long requestMediationLatency = resultSetRetrieved.getLong("requestMediationLatency");
                    long responseMediationLatency = resultSetRetrieved.getLong("responseMediationLatency");
                    long backendLatency = resultSetRetrieved.getLong("backendLatency");
                    long otherLatency = resultSetRetrieved.getLong("otherLatency");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    long time = resultSetRetrieved.getLong("time");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, tenantDomain);
                    statement2.setString(4, apiPublisher);
                    statement2.setLong(5, apiResponseTime);
                    statement2.setString(6, context);
                    statement2.setLong(7, securityLatency);
                    statement2.setLong(8, throttlingLatency);
                    statement2.setLong(9, requestMediationLatency);
                    statement2.setLong(10, responseMediationLatency);
                    statement2.setLong(11, backendLatency);
                    statement2.setLong(12, otherLatency);
                    String dayInString = year + "-" + month + "-" + day;
                    statement2.setLong(13, getTimestampOfDay(dayInString));
                    statement2.setLong(14, time);
                    statement2.setLong(15, time);
                    statement2.setString(16, result[0]);
                    statement2.setString(17, result[1]);
                    statement2.setString(18, result[2]);
                    statement2.executeUpdate();
                }
            } else {
                String msg = APIMStatMigrationConstants.API_EXE_TME_DAY_SUMMARY + " Table not exists.";
                log.error(msg);
                return;
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_EXE_TIME_HOUR_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateExecutionTimeHourSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_EXE_TIME_HOUR_SUMMARY, con1)) { //Tables exist
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_EXE_TIME_HOUR_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_EXEC_TIME_AGG
                        + "_HOURS(apiName, apiVersion, apiCreatorTenantDomain, apiCreator, AGG_SUM_responseTime, apiContext, "
                        + "AGG_SUM_securityLatency, AGG_SUM_throttlingLatency, AGG_SUM_requestMedLat, "
                        + "AGG_SUM_responseMedLat, AGG_SUM_backendLatency, AGG_SUM_otherLatency, AGG_TIMESTAMP, "
                        + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, apiHostname, apiResourceTemplate, apiMethod, "
                        + "regionalID, AGG_COUNT) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'default',1)";
                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String version = resultSetRetrieved.getString("version");
                    String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    //Remove Super Tenant Domain from APIPublisher
                    if (apiPublisher.contains(SUPER_TENANT_DOMAIN)) {
                        apiPublisher = apiPublisher.substring(0, apiPublisher.lastIndexOf(AT_IDENTIFIER));
                    }
                    //Get method, ResourceTemplate and hostName
                    String[] result = getResourceByAPI(api, version, apiPublisher);
                    long apiResponseTime = resultSetRetrieved.getLong("apiResponseTime");
                    String context = resultSetRetrieved.getString("context");
                    long securityLatency = resultSetRetrieved.getLong("securityLatency");
                    long throttlingLatency = resultSetRetrieved.getLong("throttlingLatency");
                    long requestMediationLatency = resultSetRetrieved.getLong("requestMediationLatency");
                    long responseMediationLatency = resultSetRetrieved.getLong("responseMediationLatency");
                    long backendLatency = resultSetRetrieved.getLong("backendLatency");
                    long otherLatency = resultSetRetrieved.getLong("otherLatency");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    int hour = resultSetRetrieved.getInt("hour");
                    long time = resultSetRetrieved.getLong("time");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, tenantDomain);
                    statement2.setString(4, apiPublisher);
                    statement2.setLong(5, apiResponseTime);
                    statement2.setString(6, context);
                    statement2.setLong(7, securityLatency);
                    statement2.setLong(8, throttlingLatency);
                    statement2.setLong(9, requestMediationLatency);
                    statement2.setLong(10, responseMediationLatency);
                    statement2.setLong(11, backendLatency);
                    statement2.setLong(12, otherLatency);
                    String hourInString = year + "-" + month + "-" + day + " " + hour;
                    statement2.setLong(13, getTimestampOfHour(hourInString));
                    statement2.setLong(14, time);
                    statement2.setLong(15, time);
                    statement2.setString(16, result[0]);
                    statement2.setString(17, result[1]);
                    statement2.setString(18, result[2]);
                    statement2.executeUpdate();
                }
            } else {
                String msg = APIMStatMigrationConstants.API_EXE_TIME_HOUR_SUMMARY + " Table does not exists.";
                log.error(msg);
                return;
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_EXE_TIME_MIN_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateExecutionTimeMinuteSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_EXE_TIME_MIN_SUMMARY, con1)) { //Tables exist
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_EXE_TIME_MIN_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_EXEC_TIME_AGG
                        + "_MINUTES(apiName, apiVersion, apiCreatorTenantDomain, apiCreator, AGG_SUM_responseTime, apiContext, "
                        + "AGG_SUM_securityLatency, AGG_SUM_throttlingLatency, AGG_SUM_requestMedLat, "
                        + "AGG_SUM_responseMedLat, AGG_SUM_backendLatency, AGG_SUM_otherLatency, AGG_TIMESTAMP, "
                        + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, apiHostname, apiResourceTemplate, apiMethod, "
                        + "regionalID, AGG_COUNT) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'default',1)";
                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String version = resultSetRetrieved.getString("version");
                    String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    if (apiPublisher.contains(SUPER_TENANT_DOMAIN)) {
                        apiPublisher = apiPublisher.substring(0, apiPublisher.lastIndexOf(AT_IDENTIFIER));
                    }
                    String[] result = getResourceByAPI(api, version, apiPublisher);
                    long apiResponseTime = resultSetRetrieved.getLong("apiResponseTime");
                    String context = resultSetRetrieved.getString("context");
                    long securityLatency = resultSetRetrieved.getLong("securityLatency");
                    long throttlingLatency = resultSetRetrieved.getLong("throttlingLatency");
                    long requestMediationLatency = resultSetRetrieved.getLong("requestMediationLatency");
                    long responseMediationLatency = resultSetRetrieved.getLong("responseMediationLatency");
                    long backendLatency = resultSetRetrieved.getLong("backendLatency");
                    long otherLatency = resultSetRetrieved.getLong("otherLatency");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    int hour = resultSetRetrieved.getInt("hour");
                    int minute = resultSetRetrieved.getInt("minutes");
                    long time = resultSetRetrieved.getLong("time");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, tenantDomain);
                    statement2.setString(4, apiPublisher);
                    statement2.setLong(5, apiResponseTime);
                    statement2.setString(6, context);
                    statement2.setLong(7, securityLatency);
                    statement2.setLong(8, throttlingLatency);
                    statement2.setLong(9, requestMediationLatency);
                    statement2.setLong(10, responseMediationLatency);
                    statement2.setLong(11, backendLatency);
                    statement2.setLong(12, otherLatency);
                    String minuteInString = year + "-" + month + "-" + day + " " + hour + ":" + minute;
                    statement2.setLong(13, getTimestampOfMinute(minuteInString));
                    statement2.setLong(14, time);
                    statement2.setLong(15, time);
                    statement2.setString(16, result[0]);
                    statement2.setString(17, result[1]);
                    statement2.setString(18, result[2]);
                    statement2.executeUpdate();
                }
            } else {
                String msg =  APIMStatMigrationConstants.API_EXE_TIME_MIN_SUMMARY + " Table does not exists.";
                log.error(msg);
                return;
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_THROTTLED_OUT_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateThrottledOutSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        PreparedStatement statement4 = null;
        PreparedStatement statement5 = null;
        ResultSet resultSetRetrieved = null;
        ResultSet ifExistresultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_THROTTLED_OUT_SUMMARY, con1)) { //Tables exist
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_THROTTLED_OUT_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_THROTTLED_OUT_AGG
                        + "_DAYS(apiName, apiVersion, apiContext, apiCreator, applicationName, apiCreatorTenantDomain, AGG_TIMESTAMP, "
                        + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, AGG_COUNT, throttledOutReason, applicationId, hostname, "
                        + "gatewayType, regionalID) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,'SYNAPSE','default')";
                String ifExistQuery = "Select * from " + APIMStatMigrationConstants.API_THROTTLED_OUT_AGG
                        + "_DAYS where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                        "AND apiName=? AND apiVersion=?";
                String updateQuery = "Update " + APIMStatMigrationConstants.API_THROTTLED_OUT_AGG
                        + "_DAYS Set AGG_COUNT=AGG_COUNT+? where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                        "AND apiName=? AND apiVersion=?";

                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                statement4 = con2.prepareStatement(ifExistQuery);
                statement5 = con2.prepareStatement(updateQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String api_version = resultSetRetrieved.getString("api_version");
                    String context = resultSetRetrieved.getString("context");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    String applicationName = resultSetRetrieved.getString("applicationName");
                    int applicationId = getApplicationIdByAppName(applicationName);
                    // If Application is not in the AM_APPLICATION table migration does not happen
                    if (applicationId == -1) {
                        continue;
                    }
                    String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                    long throttleout_count = resultSetRetrieved.getLong("throttleout_count");
                    String throttledOutReason = resultSetRetrieved.getString("throttledOutReason");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    String version = null;
                    if (api_version.contains(":v")) {
                        version = api_version.split(":v")[1];
                    } else if (api_version.contains(":")) {
                        version = api_version.split(":")[1];
                    }
                    String hostName = getHostNameByAPI(api, version, apiPublisher);
                    String time = resultSetRetrieved.getString("time");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, context);
                    statement2.setString(4, apiPublisher);
                    statement2.setString(5, applicationName);
                    statement2.setString(6, tenantDomain);
                    String dayInString = year + "-" + month + "-" + day;
                    statement2.setLong(7, getTimestampOfDay(dayInString));
                    statement2.setLong(8, getTimestamp(time));
                    statement2.setLong(9, getTimestamp(time));
                    statement2.setLong(10, throttleout_count);
                    statement2.setString(11, throttledOutReason);
                    statement2.setInt(12, applicationId);
                    statement2.setString(13, hostName);

                    statement4.setLong(1, getTimestampOfDay(dayInString));
                    statement4.setLong(2, getTimestamp(time));
                    statement4.setString(3, Integer.toString(applicationId));
                    statement4.setString(4, context);
                    statement4.setString(5, api);
                    statement4.setString(6, version);
                    ifExistresultSetRetrieved = statement4.executeQuery();
                    //Check result already exists or not
                    if (!ifExistresultSetRetrieved.next()) {
                        statement2.executeUpdate();
                    } else {
                        statement5.setLong(1, throttleout_count);
                        statement5.setLong(2, getTimestampOfDay(dayInString));
                        statement5.setLong(3, getTimestamp(time));
                        statement5.setString(4, Integer.toString(applicationId));
                        statement5.setString(5, context);
                        statement5.setString(6, api);
                        statement5.setString(7, version);
                        statement5.executeUpdate();
                    }
                }
            } else {
                String msg = APIMStatMigrationConstants.API_THROTTLED_OUT_SUMMARY + " Table does not exists.";
                log.error(msg);
                return;
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_THROTTLED_OUT_SUMMARY table for success counts
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateThrottledOutRequestCountSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            String retrieveQuery = "SELECT api, api_version, apiPublisher, applicationName, tenantDomain, "
                    + "sum(throttleout_count) as throttledCount, sum(success_request_count) as successCount, year, " +
                    "month, day, time FROM " +
                    APIMStatMigrationConstants.API_THROTTLED_OUT_SUMMARY + " group by api, api_version, apiPublisher, "
                    + "tenantDomain, applicationName, year, month, day, week, time";
            String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.APIM_REQ_COUNT_AGG
                    + "_DAYS(apiName, apiVersion, apiCreator, applicationName, apiCreatorTenantDomain, AGG_TIMESTAMP, "
                    + "AGG_EVENT_TIMESTAMP, AGG_SUM_successCount, AGG_SUM_throttleCount, regionalID) VALUES(?,?,?,?,?,?,?,?,?,'default')";
            statement1 = con1.prepareStatement(retrieveQuery);
            statement2 = con2.prepareStatement(insertQuery);
            resultSetRetrieved = statement1.executeQuery();
            while (resultSetRetrieved.next()) {
                String api = resultSetRetrieved.getString("api");
                String api_version = resultSetRetrieved.getString("api_version");
                String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                String applicationName = resultSetRetrieved.getString("applicationName");
                String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                long throttledCount = resultSetRetrieved.getLong("throttledCount");
                long successCount = resultSetRetrieved.getLong("successCount");
                int year = resultSetRetrieved.getInt("year");
                int month = resultSetRetrieved.getInt("month");
                int day = resultSetRetrieved.getInt("day");
                String time = resultSetRetrieved.getString("time");
                statement2.setString(1, api);
                String version = null;
                if (api_version.contains(":v")) {
                    version = api_version.split(":v")[1];
                } else if (api_version.contains(":")) {
                    version = api_version.split(":")[1];
                }
                statement2.setString(2, version);
                statement2.setString(3, apiPublisher);
                statement2.setString(4, applicationName);
                statement2.setString(5, tenantDomain);
                String dayInString = year + "-" + month + "-" + day;
                statement2.setLong(6, getTimestampOfDay(dayInString));
                statement2.setLong(7, getTimestamp(time));
                statement2.setLong(8, successCount);
                statement2.setLong(9, throttledCount);
                statement2.executeUpdate();
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method migrates the data related to the API_REQUEST_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateRequestSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        Connection con3 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        PreparedStatement statement3 = null;
        PreparedStatement statement4 = null;
        PreparedStatement statement5 = null;
        ResultSet resultSetRetrieved = null;
        ResultSet ifExistresultSetRetrieved = null;
        ResultSet resultSetFromAMDB = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            con3 = apimDataSource.getConnection();
            String consumerKeyMappingQuery = "SELECT APPLICATION_ID FROM AM_APPLICATION_KEY_MAPPING WHERE CONSUMER_KEY=?";
            String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_REQUEST_SUMMARY;
            String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_USER_PER_APP_AGG
                    + "_DAYS(apiName, apiVersion, apiCreator, username, apiContext, AGG_COUNT, apiHostname, "
                    + "AGG_TIMESTAMP, AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, applicationId, userTenantDomain, "
                    + "gatewayType, label, regionalID, apiCreatorTenantDomain, applicationOwner, applicationName) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,'SYNAPSE','Synapse','default',?,?,?)";
            String ifExistQuery = "Select * from " + APIMStatMigrationConstants.API_USER_PER_APP_AGG
                    + "_DAYS where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                    "AND apiName=? AND apiVersion=?";
            String updateQuery = "Update " + APIMStatMigrationConstants.API_USER_PER_APP_AGG
                    + "_DAYS Set AGG_COUNT=AGG_COUNT+? where AGG_TIMESTAMP=? AND AGG_EVENT_TIMESTAMP=? AND applicationId=? AND apiContext=? " +
                    "AND apiName=? AND apiVersion=?";
            statement1 = con1.prepareStatement(retrieveQuery);
            statement2 = con2.prepareStatement(insertQuery);
            statement3 = con3.prepareStatement(consumerKeyMappingQuery);
            statement4 = con2.prepareStatement(ifExistQuery);
            statement5 = con2.prepareStatement(updateQuery);
            resultSetRetrieved = statement1.executeQuery();
            String[] result = new String[2];
            while (resultSetRetrieved.next()) {
                String api = resultSetRetrieved.getString("api");
                String version = resultSetRetrieved.getString("version");
                String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                String userTenantDomain = null;
                String apiCreatorTenantDomain = null;
                String consumerKey = resultSetRetrieved.getString("consumerKey");
                int applicationId = -1;
                // Unauthenticated API, Application Id set to 0.
                if (consumerKey == null || consumerKey.equalsIgnoreCase(DASH_IDENTIFIER)) {
                    applicationId = 0;
                } else {
                    statement3.setString(1, consumerKey);
                    resultSetFromAMDB = statement3.executeQuery();
                    //ConsumerKey not in the AM_APPLICATION_KEY_MAPPING table.
                    if (!resultSetFromAMDB.next()) {
                        // Remove from the migrate data
                        log.info("WSO2 API-M Migration Task : ConsumerKey " + consumerKey + " Does not contain in the " +
                                "AM_APPLICATION_KEY_MAPPING table.");
                        continue;
                    } else {
                        applicationId = resultSetFromAMDB.getInt("APPLICATION_ID");
                    }
                }
                if (applicationId == 0) {
                    result[0] = UNAUTHENTICATED_API_IDENTIFIER;
                    result[1] = UNAUTHENTICATED_API_IDENTIFIER;
                } else {
                    result = getApplicationNameAndOwnerByAppID(applicationId);
                }
                String userId = resultSetRetrieved.getString("userId");
                String context = resultSetRetrieved.getString("context");
                //Set ApiCreater Tenant Domain
                if (context.contains(TENANT_DOMAIN_IDENTIFIER)) {
                    apiCreatorTenantDomain = apiPublisher.substring(apiPublisher.lastIndexOf(AT_IDENTIFIER) + 1,
                            apiPublisher.length());
                } else {
                    apiCreatorTenantDomain = SUPER_TENANT_DOMAIN;
                }
                //Set User Tenant Domain
                if (userId.equals(ANONYMOUS_USER)) {
                    userTenantDomain = SUPER_TENANT_DOMAIN;
                } else {
                    userTenantDomain = userId.substring(userId.lastIndexOf(AT_IDENTIFIER) + 1, userId.length());
                }
                long total_request_count = resultSetRetrieved.getLong("total_request_count");
                String hostName = resultSetRetrieved.getString("hostName");
                int year = resultSetRetrieved.getInt("year");
                int month = resultSetRetrieved.getInt("month");
                int day = resultSetRetrieved.getInt("day");
                String time = resultSetRetrieved.getString("time");
                statement2.setString(1, api);
                statement2.setString(2, version);
                statement2.setString(3, apiPublisher);
                statement2.setString(4, userId);
                statement2.setString(5, context);
                statement2.setLong(6, total_request_count);
                statement2.setString(7, hostName);
                String dayInString = year + "-" + month + "-" + day;
                statement2.setLong(8, getTimestampOfDay(dayInString));
                statement2.setLong(9, getTimestamp(time));
                statement2.setLong(10, getTimestamp(time));
                if (applicationId != -1) {
                    statement2.setString(11, Integer.toString(applicationId));
                } else {
                    String errorMsg = "Error occurred while retrieving applicationId for consumer key : " + consumerKey;
                    log.error(errorMsg);
                    throw new APIMStatMigrationException(errorMsg);
                }
                statement2.setString(12, userTenantDomain);
                statement2.setString(13, apiCreatorTenantDomain);
                statement2.setString(14, result[1]);
                statement2.setString(15, result[0]);

                statement4.setLong(1, getTimestampOfDay(dayInString));
                statement4.setLong(2, getTimestamp(time));
                statement4.setString(3, Integer.toString(applicationId));
                statement4.setString(4, context);
                statement4.setString(5, api);
                statement4.setString(6, version);
                ifExistresultSetRetrieved = statement4.executeQuery();
                //Check if result exists or not
                if (!ifExistresultSetRetrieved.next()) {
                    statement2.executeUpdate();
                } else {
                    statement5.setLong(1, total_request_count);
                    statement5.setLong(2, getTimestampOfDay(dayInString));
                    statement5.setLong(3, getTimestamp(time));
                    statement5.setString(4, Integer.toString(applicationId));
                    statement5.setString(5, context);
                    statement5.setString(6, api);
                    statement5.setString(7, version);
                    statement5.executeUpdate();
                }
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
            closeDatabaseLinks(resultSetFromAMDB, statement3, con3);
        }
    }

    /**
     * This method sort the graph QL operation stored as API Resource Template into an alphabetical order
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void sortGraphQLOperation() throws APIMStatMigrationException {
        Connection con = null;
        PreparedStatement retrievingStatement = null;
        PreparedStatement retrievingCountStatement = null;
        PreparedStatement updateStatement = null;
        PreparedStatement updateCountStatement = null;
        PreparedStatement deleteStatement = null;
        ResultSet resultSetRetrieved = null;
        String[] tableNamesArray = {APIMStatMigrationConstants.API_RESOURCE_PATH_AGG,
                APIMStatMigrationConstants.API_EXEC_TIME_AGG};
        String[] granularities = {"SECONDS", "MINUTES", "HOURS", "DAYS", "MONTHS", "YEARS"};
        try {
            con = newStatsDataSource.getConnection();
            for (String aggName : tableNamesArray) {
                for (String granularity : granularities) {
                    String tableName = aggName + "_" + granularity;
                    if (isTableExist(tableName, con)) {
                        String retrieveQuery = "SELECT * from " + tableName;
                        String retrieveCountQuery = "Select count(apiName) as count from " + tableName + " where " +
                                "apiResourceTemplate = ? and AGG_TIMESTAMP = ? and AGG_EVENT_TIMESTAMP = ? " +
                                "and apiContext = ? and apiMethod = ? and apiHostname = ?";
                        String deleteQuery = "delete  from " + tableName + " where " +
                                "apiResourceTemplate = ? and AGG_TIMESTAMP = ? and AGG_EVENT_TIMESTAMP = ? " +
                                "and apiContext = ? and apiMethod = ? and apiHostname = ?";
                        String updateQuery = "UPDATE " + tableName + " set apiResourceTemplate = ? " +
                                "WHERE apiResourceTemplate = ? and AGG_TIMESTAMP = ? and AGG_EVENT_TIMESTAMP = ? " +
                                "and apiContext = ? and apiMethod = ? and apiHostname = ?";
                        String updateCountQuery = "UPDATE " + tableName + " set AGG_COUNT = AGG_COUNT + ? " +
                                "WHERE apiResourceTemplate = ? and AGG_TIMESTAMP = ? and AGG_EVENT_TIMESTAMP = ? " +
                                "and apiContext = ? and apiMethod = ? and apiHostname = ? ";

                        if (tableName.contains(APIMStatMigrationConstants.API_RESOURCE_PATH_AGG)) {
                            retrieveCountQuery = retrieveCountQuery + " and applicationId = ?";
                            updateQuery = updateQuery + " and applicationId = ?";
                            updateCountQuery = updateCountQuery + " and applicationId = ?";
                            deleteQuery = deleteQuery + " and applicationId = ?";
                        }
                        retrievingStatement = con.prepareStatement(retrieveQuery);
                        updateStatement = con.prepareStatement(updateQuery);
                        updateCountStatement = con.prepareStatement(updateCountQuery);
                        deleteStatement = con.prepareStatement(deleteQuery);
                        resultSetRetrieved = retrievingStatement.executeQuery();
                        while (resultSetRetrieved.next()) {
                            String resourceTemplate = resultSetRetrieved.getString(
                                    APIMStatMigrationConstants.API_RESOURCE_TEMPLATE);
                            String aggTimestamp = resultSetRetrieved.getString(
                                    APIMStatMigrationConstants.AGG_TIMESTAMP);
                            String aggEventTimestamp = resultSetRetrieved.getString(
                                    APIMStatMigrationConstants.AGG_EVENT_TIMESTAMP);
                            String apiContext = resultSetRetrieved.getString(
                                    APIMStatMigrationConstants.API_CONTEXT);
                            String apiHostname = resultSetRetrieved.getString(APIMStatMigrationConstants.API_HOSTNAME);
                            String apiMethod = resultSetRetrieved.getString(
                                    APIMStatMigrationConstants.API_METHOD);
                            String aggCount = resultSetRetrieved.getString(APIMStatMigrationConstants.AGG_COUNT);
                            String applicationId = null;
                            if (tableName.contains(APIMStatMigrationConstants.API_RESOURCE_PATH_AGG)) {
                                applicationId = resultSetRetrieved.getString(
                                        APIMStatMigrationConstants.APPLICATION_ID);
                            }
                            String[] splittedOperation = resourceTemplate.split(",");
                            if (splittedOperation.length > 1) {
                                Arrays.sort(splittedOperation);
                                String sortedOperations = String.join(",", splittedOperation);
                                if (!sortedOperations.equals(resourceTemplate)) {
                                    retrievingCountStatement = con.prepareStatement(retrieveCountQuery);
                                    retrievingCountStatement.setString(1, sortedOperations);
                                    retrievingCountStatement.setString(2, aggTimestamp);
                                    retrievingCountStatement.setString(3, aggEventTimestamp);
                                    retrievingCountStatement.setString(4, apiContext);
                                    retrievingCountStatement.setString(5, apiMethod);
                                    retrievingCountStatement.setString(6, apiHostname);
                                    if (tableName.contains(APIMStatMigrationConstants.API_RESOURCE_PATH_AGG)) {
                                        retrievingCountStatement.setString(7, applicationId);
                                    }
                                    ResultSet countSet = retrievingCountStatement.executeQuery();
                                    int count = 0;
                                    while (countSet.next()) {
                                        count = Integer.parseInt(countSet.getString(APIMStatMigrationConstants.COUNT));
                                    }
                                    if (count > 0) {
                                        updateCountStatement.setString(1, aggCount);
                                        updateCountStatement.setString(2, sortedOperations);
                                        updateCountStatement.setString(3, aggTimestamp);
                                        updateCountStatement.setString(4, aggEventTimestamp);
                                        updateCountStatement.setString(5, apiContext);
                                        updateCountStatement.setString(6, apiMethod);
                                        updateCountStatement.setString(7, apiHostname);
                                        deleteStatement.setString(1, resourceTemplate);
                                        deleteStatement.setString(2, aggTimestamp);
                                        deleteStatement.setString(3, aggEventTimestamp);
                                        deleteStatement.setString(4, apiContext);
                                        deleteStatement.setString(5, apiMethod);
                                        deleteStatement.setString(6, apiHostname);
                                        if (tableName.contains(APIMStatMigrationConstants.API_RESOURCE_PATH_AGG)) {
                                            updateCountStatement.setString(8, applicationId);
                                            deleteStatement.setString(7, applicationId);
                                        }
                                        updateCountStatement.addBatch();
                                        deleteStatement.addBatch();
                                    } else {
                                        updateStatement.setString(1, sortedOperations);
                                        updateStatement.setString(2, resourceTemplate);
                                        updateStatement.setString(3, aggTimestamp);
                                        updateStatement.setString(4, aggEventTimestamp);
                                        updateStatement.setString(5, apiContext);
                                        updateStatement.setString(6, apiMethod);
                                        updateStatement.setString(7, apiHostname);
                                        if (tableName.contains(APIMStatMigrationConstants.API_RESOURCE_PATH_AGG)) {
                                            updateStatement.setString(8, applicationId);
                                        }
                                        updateStatement.addBatch();
                                    }
                                }
                            }
                        }
                        updateCountStatement.executeBatch();
                        updateStatement.executeBatch();
                        deleteStatement.executeBatch();
                    } else {
                        String msg = tableName + " Table does not exists.";
                        log.error(msg);
                    }
                }
            }
        } catch (SQLException ex) {
            String msg = "Error while sorting GraphQL operations";
            throw new APIMStatMigrationException(msg, ex);
        }
    }

    /**
     * This method migrates the data related to the API_REQ_GEO_LOC_SUMMARY table
     *
     * @throws APIMStatMigrationException on error
     */
    @Override
    public void migrateGeoLocationSummaryTable() throws APIMStatMigrationException {
        Connection con1 = null;
        Connection con2 = null;
        PreparedStatement statement1 = null;
        PreparedStatement statement2 = null;
        ResultSet resultSetRetrieved = null;
        try {
            con1 = oldStatsDataSource.getConnection();
            con2 = newStatsDataSource.getConnection();
            if (isTableExist(APIMStatMigrationConstants.API_REQ_GEO_LOC_SUMMARY, con1)) { //Tables exist
                String retrieveQuery = "SELECT * FROM " + APIMStatMigrationConstants.API_REQ_GEO_LOC_SUMMARY;
                String insertQuery = "INSERT INTO " + APIMStatMigrationConstants.API_GEO_LOCATION_AGG
                        + "_DAYS(apiName, apiVersion, apiCreator, apiCreatorTenantDomain, totalCount, AGG_TIMESTAMP, "
                        + "AGG_EVENT_TIMESTAMP, AGG_LAST_EVENT_TIMESTAMP, country, city, apiContext, regionalID) VALUES(?,?,?,?,?,?,?,?,?,?,?,'default')";
                statement1 = con1.prepareStatement(retrieveQuery);
                statement2 = con2.prepareStatement(insertQuery);
                resultSetRetrieved = statement1.executeQuery();
                while (resultSetRetrieved.next()) {
                    String api = resultSetRetrieved.getString("api");
                    String version = resultSetRetrieved.getString("version");
                    String apiPublisher = resultSetRetrieved.getString("apiPublisher");
                    String tenantDomain = resultSetRetrieved.getString("tenantDomain");
                    String context = getContextByAPI(api, version, apiPublisher);
                    //API does not contain in AM_API table
                    if (context == null) {
                        continue;
                    }
                    long total_request_count = resultSetRetrieved.getLong("total_request_count");
                    int year = resultSetRetrieved.getInt("year");
                    int month = resultSetRetrieved.getInt("month");
                    int day = resultSetRetrieved.getInt("day");
                    long time = resultSetRetrieved.getLong("requestTime");
                    String country = resultSetRetrieved.getString("country");
                    String city = resultSetRetrieved.getString("city");
                    statement2.setString(1, api);
                    statement2.setString(2, version);
                    statement2.setString(3, apiPublisher);
                    statement2.setString(4, tenantDomain);
                    statement2.setLong(5, total_request_count);
                    String dayInString = year + "-" + month + "-" + day;
                    statement2.setLong(6, getTimestampOfDay(dayInString));
                    statement2.setLong(7, time);
                    statement2.setLong(8, time);
                    statement2.setString(9, country);
                    statement2.setString(10, city); //check if ok to be null
                    statement2.setString(11, context);
                    statement2.executeUpdate();
                }
            } else {
                String msg = APIMStatMigrationConstants.API_REQ_GEO_LOC_SUMMARY + " Table does not exists.";
                log.error(msg);
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } catch (Exception e) {
            String msg = "Generic error occurred while connecting to the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetRetrieved, statement1, con1);
            closeDatabaseLinks(null, statement2, con2);
        }
    }

    /**
     * This method is used to close the ResultSet, PreparedStatement and Connection after getting data from the DB
     * This is called if a "PreparedStatement" is used to fetch results from the DB
     *
     * @param resultSet         ResultSet returned from the database query
     * @param preparedStatement prepared statement used in the database query
     * @param connection        DB connection used to get data from the database
     */
    private static void closeDatabaseLinks(ResultSet resultSet, PreparedStatement preparedStatement,
                                           Connection connection) {

        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                //this is logged and the process is continued because the query has executed
                log.error("WSO2 API-M Migration Task : Error occurred while closing the result set from JDBC database.", e);
            }
        }
        if (preparedStatement != null) {
            try {
                preparedStatement.close();
            } catch (SQLException e) {
                //this is logged and the process is continued because the query has executed
                log.error("WSO2 API-M Migration Task : Error occurred while closing the prepared statement from "
                        + "JDBC database.", e);
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                //this is logged and the process is continued because the query has executed
                log.error("WSO2 API-M Migration Task : Error occurred while closing the JDBC database connection.", e);
            }
        }
    }

    /**
     * This method returns the date of form yyyy-MM-dd HH:mm as a timestamp
     *
     * @param date date as a string
     * @return the date in milliseconds
     */
    private static long getTimestamp(String date) {
        //Check the date pattern yyyy-MM-dd HH:mm:ss and remove ":ss"
        if (StringUtils.countMatches(date, COLON_IDENTIFIER) != 1) {
            date = date.substring(0, date.lastIndexOf(COLON_IDENTIFIER));
        }
        DateTimeFormatter formatter = DateTimeFormat.forPattern(APIMStatMigrationConstants.TIMESTAMP_PATTERN);
        DateTime dateTime = formatter.parseDateTime(date);
        return dateTime.getMillis();
    }

    /**
     * This method returns the date of form yyyy-M-dd as a timestamp
     *
     * @param date date as a string
     * @return the date in milliseconds
     */
    private static long getTimestampOfDay(String date) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(APIMStatMigrationConstants.TIMESTAMP_DAY_PATTERN);
        DateTime dateTime = formatter.parseDateTime(date);
        return dateTime.getMillis();
    }

    /**
     * This method returns the date of form yyyy-M-dd HH as a timestamp
     *
     * @param date date as a string
     * @return the date in milliseconds
     */
    private static long getTimestampOfHour(String date) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(APIMStatMigrationConstants.TIMESTAMP_HOUR_PATTERN);
        DateTime dateTime = formatter.parseDateTime(date);
        return dateTime.getMillis();
    }

    /**
     * This method returns the date of form yyyy-MM-dd HH:mm as a timestamp
     *
     * @param date date as a string
     * @return the date in milliseconds
     */
    private static long getTimestampOfMinute(String date) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(APIMStatMigrationConstants.TIMESTAMP_MINUTE_PATTERN);
        DateTime dateTime = formatter.parseDateTime(date);
        return dateTime.getMillis();
    }

    /**
     * This method returns the ApplicationId
     *
     * @param appName applicationName as a string
     * @return the ApplicationId in integer
     */
    private int getApplicationIdByAppName(String appName) throws APIMStatMigrationException {
        Connection con1 = null;
        PreparedStatement statement1 = null;
        ResultSet resultSetFromAMDB = null;
        int applicationId = 0;
        try {
            con1 = apimDataSource.getConnection();
            String appIdQuery = "SELECT APPLICATION_ID FROM AM_APPLICATION WHERE NAME=?";

            statement1 = con1.prepareStatement(appIdQuery);
            statement1.setString(1, appName);
            resultSetFromAMDB = statement1.executeQuery();
            if (!resultSetFromAMDB.next()) {
                applicationId = -1;
            } else {
                do {
                    applicationId = resultSetFromAMDB.getInt("APPLICATION_ID");
                    continue;
                } while (resultSetFromAMDB.next());
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetFromAMDB, statement1, con1);
        }
        return applicationId;
    }

    /**
     * This method returns the ApplicationName And Owner
     *
     * @param appId date as a int
     * @return the ApplicationNameAndOwner in string Array
     */
    private String[] getApplicationNameAndOwnerByAppID(int appId) throws APIMStatMigrationException {
        Connection con1 = null;
        PreparedStatement statement1 = null;
        ResultSet resultSetFromAMDB = null;
        String[] result = new String[3];

        try {
            con1 = apimDataSource.getConnection();
            String appIdQuery = "SELECT NAME,CREATED_BY FROM AM_APPLICATION WHERE APPLICATION_ID=?";

            statement1 = con1.prepareStatement(appIdQuery);
            statement1.setInt(1, appId);
            resultSetFromAMDB = statement1.executeQuery();
            while (resultSetFromAMDB.next()) {
                result[0] = resultSetFromAMDB.getString("NAME");
                result[1] = resultSetFromAMDB.getString("CREATED_BY");
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetFromAMDB, statement1, con1);
        }
        return result;
    }

    /**
     * This method returns Resource
     *
     * @param api       date as a string
     * @param version   date as a string
     * @param publisher date as a string
     * @return the Resource in string Array
     */
    private String[] getResourceByAPI(String api, String version, String publisher) throws APIMStatMigrationException {
        Connection con1 = null;
        PreparedStatement statement1 = null;
        ResultSet resultSetFromSPDB = null;
        String[] result = new String[3];

        try {
            con1 = oldStatsDataSource.getConnection();
            String resourceQuery = "SELECT resourcePath,method,hostName FROM " + APIMStatMigrationConstants.
                    API_RESOURCE_USAGE_SUMMARY + " where api=? AND version=? " +
                    "AND apiPublisher=?";

            statement1 = con1.prepareStatement(resourceQuery);
            statement1.setString(1, api);
            statement1.setString(2, version);
            statement1.setString(3, publisher);
            resultSetFromSPDB = statement1.executeQuery();
            if (!resultSetFromSPDB.next()) {
                result[0] = UNAUTHENTICATED_API_IDENTIFIER;
                result[1] = UNAUTHENTICATED_API_IDENTIFIER;
                result[2] = UNAUTHENTICATED_API_IDENTIFIER;
            } else {
                do {
                    result[0] = resultSetFromSPDB.getString("hostName");
                    result[1] = resultSetFromSPDB.getString("resourcePath");
                    result[2] = resultSetFromSPDB.getString("method");
                    continue;
                } while (resultSetFromSPDB.next());
            }

        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetFromSPDB, statement1, con1);
        }
        return result;
    }

    /**
     * This method returns the HostName
     *
     * @param api       date as a string
     * @param version   date as a string
     * @param publisher date as a string
     * @return the HostName in String
     */
    private String getHostNameByAPI(String api, String version, String publisher) throws APIMStatMigrationException {
        Connection con1 = null;
        PreparedStatement statement1 = null;
        ResultSet resultSetFromSPDB = null;
        String result = null;

        try {
            con1 = oldStatsDataSource.getConnection();
            String resourceQuery = "SELECT hostName FROM " + APIMStatMigrationConstants.
                    API_RESOURCE_USAGE_SUMMARY + " where api=? AND version=? " +
                    "AND apiPublisher=?";
            statement1 = con1.prepareStatement(resourceQuery);
            statement1.setString(1, api);
            statement1.setString(2, version);
            statement1.setString(3, publisher);
            resultSetFromSPDB = statement1.executeQuery();
            if (!resultSetFromSPDB.next()) {
                result = UNAUTHENTICATED_API_IDENTIFIER;
            } else {
                do {
                    result = resultSetFromSPDB.getString("hostName");
                    continue;
                } while (resultSetFromSPDB.next());
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetFromSPDB, statement1, con1);
        }
        return result;
    }

    /**
     * This method returns the ApplicationName
     *
     * @param appId date as a string
     * @return the ApplicationName in String
     */
    private String getApplicationNameByAppID(int appId) throws APIMStatMigrationException {
        Connection con1 = null;
        PreparedStatement statement1 = null;
        ResultSet resultSetFromAMDB = null;
        String result = null;

        try {
            con1 = apimDataSource.getConnection();
            String appIdQuery = "SELECT NAME FROM AM_APPLICATION WHERE APPLICATION_ID=?";

            statement1 = con1.prepareStatement(appIdQuery);
            statement1.setInt(1, appId);
            resultSetFromAMDB = statement1.executeQuery();
            while (resultSetFromAMDB.next()) {
                result = resultSetFromAMDB.getString("NAME");
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetFromAMDB, statement1, con1);
        }
        return result;
    }

    /**
     * This method returns the Context
     *
     * @param apiName   date as a string
     * @param version   date as a string
     * @param createdBy date as a string
     * @return the Context in String
     */
    private String getContextByAPI(String apiName, String version, String createdBy) throws APIMStatMigrationException {
        Connection con1 = null;
        PreparedStatement statement1 = null;
        ResultSet resultSetFromAMDB = null;
        String context = null;
        try {
            con1 = apimDataSource.getConnection();
            String contextQuery = "select CONTEXT from AM_API where API_NAME=? AND API_VERSION=? AND CREATED_BY=?";
            statement1 = con1.prepareStatement(contextQuery);
            statement1.setString(1, apiName);
            statement1.setString(2, version);
            statement1.setString(3, createdBy);
            resultSetFromAMDB = statement1.executeQuery();
            //API delete from AM_API table
            if (!resultSetFromAMDB.next()) {
                context = null;
            } else {
                context = resultSetFromAMDB.getString("CONTEXT");
            }
        } catch (SQLException e) {
            String msg = "Error occurred while connecting to and querying from the database";
            log.error(msg, e);
            throw new APIMStatMigrationException(msg, e);
        } finally {
            closeDatabaseLinks(resultSetFromAMDB, statement1, con1);
        }
        return context;
    }

    /**
     * This method find the existence of the table in given RDBMS
     *
     * @param tableName  Name of the table
     * @param connection Database connection
     * @return return boolean to indicate it's existence
     * @throws SQLException throws if database exception occurred
     */
    private boolean isTableExist(String tableName, Connection connection) throws SQLException {
        final String checkTableSQLQuery = "SELECT DISTINCT 1 FROM " + tableName;
        Statement statement = null;
        ResultSet rs = null;
        try {
            statement = connection.createStatement();
            rs = statement.executeQuery(checkTableSQLQuery);
            return true;
        } catch (SQLException e) {
            // SQL error related to table not exist is db specific
            // error is logged and continues.
            log.error("WSO2 API-M Migration Task : Error occurred while checking existence of the table:" + tableName, e);
            return false;
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    // this is logged and the process is continued because the
                    // query has executed
                    log.error("WSO2 API-M Migration Task : Error occurred while closing the result set from JDBC"
                            + " database.", e);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    // this is logged and the process is continued because the
                    // query has executed
                    log.error("WSO2 API-M Migration Task : Error occurred while closing the prepared statement from"
                            + " JDBC database.", e);
                }
            }
            // connection object will not be closed as it should be handled by
            // the parent method.
        }
    }

}
