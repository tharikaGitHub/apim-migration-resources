package org.wso2.carbon.apimgt.migration.validator.utils;

public class UtilsFactory {
    public Utils getVersionUtils(String migrateFromVersion) {
        if ("2.6.0".equals(migrateFromVersion)) {
            return new V260Utils(migrateFromVersion);
        } else if ("3.0.0".equals(migrateFromVersion)) {
            return new V300Utils(migrateFromVersion);
        } else if ("3.1.0".equals(migrateFromVersion)) {
            return new V310Utils(migrateFromVersion);
        } else if ("3.2.0".equals(migrateFromVersion)) {
            return new V320Utils(migrateFromVersion);
        } else if ("4.0.0".equals(migrateFromVersion)) {
            return new V400Utils(migrateFromVersion);
        }
        return null;
    }
}
