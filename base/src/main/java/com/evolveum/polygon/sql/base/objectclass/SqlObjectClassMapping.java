/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.objectclass;

import org.identityconnectors.framework.common.objects.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Bridges SQL-side table metadata and ConnId-side attribute definitions.
 */
public class SqlObjectClassMapping {

    private final ObjectClass objectClass;
    private final String tableName;
    private final List<SqlAttributeMapping> attributeMappings;
    private final String uidColumnName;
    private final List<String> returnedByDefaultColumnNames;

    public SqlObjectClassMapping(ObjectClass objectClass, String tableName,
                                  List<SqlAttributeMapping> attributeMappings,
                                  String uidColumnName,
                                  List<String> returnedByDefaultColumnNames) {
        this.objectClass = objectClass;
        this.tableName = tableName;
        this.attributeMappings = attributeMappings;
        this.uidColumnName = uidColumnName;
        this.returnedByDefaultColumnNames = returnedByDefaultColumnNames;
    }

    public ObjectClass getObjectClass() {
        return objectClass;
    }

    public String getTableName() {
        return tableName;
    }

    public String getUidColumnName() {
        return uidColumnName;
    }

    public List<SqlAttributeMapping> getAttributeMappings() {
        return attributeMappings;
    }

    public SqlAttributeMapping getUidMapping() {
        for (SqlAttributeMapping m : attributeMappings) {
            if (Uid.NAME.equals(m.getConnIdName())
                    || getUidColumnName() != null && m.getSqlColumn().equals(getUidColumnName())) {
                return m;
            }
        }
        return null;
    }

    public List<String> getReturnedByDefaultAttributeNames() {
        return attributeMappings.stream()
                .filter(SqlAttributeMapping::isReturnedByDefault)
                .map(SqlAttributeMapping::getConnIdName)
                .collect(Collectors.toList());
    }

    public List<String> getReturnedByDefaultColumnNames() {
        return returnedByDefaultColumnNames;
    }

    public List<String> getAttributeNames() {
        return attributeMappings.stream()
                .map(SqlAttributeMapping::getConnIdName)
                .collect(Collectors.toList());
    }

    public SqlAttributeMapping getAttributeMapping(String connIdName) {
        for (SqlAttributeMapping m : attributeMappings) {
            if (connIdName.equals(m.getConnIdName())) {
                return m;
            }
        }
        return null;
    }

    public Object getUidValue(Map<String, Object> row) {
        if (row == null || uidColumnName == null) {
            return null;
        }
        return row.get(uidColumnName);
    }

    public String getUidValueStr(Map<String, Object> row) {
        Object value = getUidValue(row);
        return value != null ? String.valueOf(value) : null;
    }

    public ConnectorObject buildConnectorObject(Map<String, Object> row, ObjectClass oc) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(oc);

        String uidValue = getUidValueStr(row);
        if (uidValue != null) {
            builder.setUid(uidValue);
            builder.setName(new Name(uidValue));
        } else {
            builder.setUid(row.toString());
        }

        for (SqlAttributeMapping attr : attributeMappings) {
            if (attr.isReturnedByDefault()) {
                String connIdName = attr.getConnIdName();
                String sqlCol = attr.getSqlColumn();
                Object value = row.get(sqlCol);
                if (value != null) {
                    builder.addAttribute(AttributeBuilder.build(connIdName, value));
                }
            }
        }

        return builder.build();
    }

    /**
     * Maps a single ConnId attribute to its SQL column.
     */
    public static class SqlAttributeMapping {
        private final String connIdName;
        private final String sqlColumn;
        private final boolean returnedByDefault;

        public SqlAttributeMapping(String connIdName, String sqlColumn, boolean returnedByDefault) {
            this.connIdName = connIdName;
            this.sqlColumn = sqlColumn;
            this.returnedByDefault = returnedByDefault;
        }

        public String getConnIdName() {
            return connIdName;
        }

        public String getSqlColumn() {
            return sqlColumn;
        }

        public boolean isReturnedByDefault() {
            return returnedByDefault;
        }
    }
}