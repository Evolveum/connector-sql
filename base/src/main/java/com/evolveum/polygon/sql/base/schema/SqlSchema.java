/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.api.operations.APIOperation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the SQL database schema in a ConnId-compatible format.
 */
public class SqlSchema {

    private final List<SqlTableInfo> tables;
    private final Map<String, SqlTableInfo> tableByName;
    private final Map<ObjectClass, SqlTableInfo> objectClassToTable;

    public SqlSchema(List<SqlTableInfo> tables) {
        this.tables = tables;
        this.tableByName = new HashMap<>();
        this.objectClassToTable = new HashMap<>();

        for (SqlTableInfo table : tables) {
            tableByName.put(table.getName().toLowerCase(), table);
            // Map table name to ObjectClass
            String objectClassName = table.getName().toLowerCase();
            ObjectClass objectClass = new ObjectClass(objectClassName);
            objectClassToTable.put(objectClass, table);
        }
    }

    public List<SqlTableInfo> getTables() {
        return tables;
    }

    public SqlTableInfo getTable(String name) {
        return tableByName.get(name.toLowerCase());
    }

    public SqlTableInfo getTable(ObjectClass objectClass) {
        return objectClassToTable.get(objectClass);
    }

    public Schema connIdSchema() {
        Set<ObjectClassInfo> objClassInfoSet = new HashSet<>();

        for (SqlTableInfo table : tables) {
            // Map to ObjectClass
            String objectClassName = table.getName().toLowerCase();
            ObjectClass objectClass = new ObjectClass(objectClassName);
            var objClassBuilder = new ObjectClassInfoBuilder()
                    .setType(objectClassName);

            for (SqlColumnMeta column : table.getColumns()) {
                var attribute = new AttributeInfoBuilder()
                        .setName(column.getName())
                        .setType(toConnIdType(column.getTypeName()))
                        .setRequired(!column.isNullable())
                        .setReturnedByDefault(!isLargeType(column.getTypeName()))
                        .build();
                objClassBuilder.addAttributeInfo(attribute);
            }
            objClassInfoSet.add(objClassBuilder.build());
        }

        return new Schema(objClassInfoSet, Set.of(), Map.of(), Map.of());
    }

    private Class<?> toConnIdType(String typeName) {
        switch (typeName.toUpperCase()) {
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "VARCHAR2":
                return String.class;
            case "INTEGER":
            case "INT":
            case "SMALLINT":
            case "TINYINT":
            case "BIGINT":
                return Long.class;
            case "DECIMAL":
            case "NUMERIC":
            case "FLOAT":
            case "REAL":
            case "DOUBLE":
                return Double.class;
            case "BOOLEAN":
            case "BOOL":
                return Boolean.class;
            case "DATE":
            case "TIME":
            case "TIMESTAMP":
            case "DATETIME":
                return String.class;
            case "BLOB":
            case "BYTEA":
            case "VARBINARY":
                return byte[].class;
            default:
                return String.class;
        }
    }

    private boolean isLargeType(String typeName) {
        String typeUpper = typeName.toUpperCase();
        return typeUpper.contains("BLOB") || typeUpper.contains("CLOB") || 
               typeUpper.contains("BINARY") || typeUpper.contains("VARBINARY");
    }

    public Map<ObjectClass, SqlTableInfo> getObjectClassToTableMapping() {
        return objectClassToTable;
    }
}