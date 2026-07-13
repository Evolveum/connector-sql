/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.objectclass;

import org.identityconnectors.framework.common.objects.*;

import java.util.Map;

/**
 * Bridges SQL-side table metadata and ConnId-side attribute definitions.
 */
public class SqlObjectClassMapping {

    private final ObjectClass objectClass;
    private final String tableName;
    private final String uidColumnName;

    public SqlObjectClassMapping(ObjectClass objectClass, String tableName,
                                 String uidColumnName) {
        this.objectClass = objectClass;
        this.tableName = tableName;
        this.uidColumnName = uidColumnName;
    }

    public ObjectClass getObjectClass() {
        return objectClass;
    }

    public String getTableName() {
        return tableName;
    }

    public Object getUidValue(Map<String, Object> row) {
        if (row == null || uidColumnName == null) {
            return null;
        }
        return row.get(uidColumnName);
    }


    public String getUidValueStr(Map<String, Object> row) {
        var value = getUidValue(row);
        return value != null ? String.valueOf(value) : null;
    }
}