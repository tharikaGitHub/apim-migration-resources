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

public class SQLConstants {
    public static final String GET_API_ID_SQL =
            "SELECT     " +
                    "   API.API_ID " +
                    " FROM " +
                    "   AM_API API " +
                    " WHERE " +
                    "   API.API_PROVIDER = ? " +
                    "   AND API.API_NAME = ? " +
                    "   AND API.API_VERSION = ? ";

    public static final String GET_URL_TEMPLATES_BY_API_ID_SQL_260 =
            " SELECT " +
                    "   URL_PATTERN," +
                    "   HTTP_METHOD," +
                    "   AUTH_SCHEME," +
                    "   THROTTLING_TIER, " +
                    "   MEDIATION_SCRIPT " +
                    " FROM " +
                    "   AM_API_URL_MAPPING " +
                    " WHERE " +
                    "   API_ID = ? " +
                    " ORDER BY " +
                    "   URL_MAPPING_ID ASC ";
}

