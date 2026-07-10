/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.objectclass;

import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSchema;
import com.evolveum.polygon.sql.base.schema.QueryDSLMetadata;
import com.evolveum.polygon.sql.base.schema.SqlQuerydslMetadataFactory;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds SqlObjectClassMapping instances by bridging ConnId schema (BaseObjectClassDefinition)
 * and QueryDSL metadata (QueryDSLMetadata).
 */
public class SqlObjectClassMapper {

    /**
     * Builds mappings for all object classes in the schema.
     */
    public static Map<ObjectClass, SqlObjectClassMapping> buildAll(
            SqlSchema schema,
            SqlQuerydslMetadataFactory metadataFactory) {
        if (schema == null) {
            return Map.of();
        }
        Map<ObjectClass, SqlObjectClassMapping> result = new java.util.LinkedHashMap<>();
        for (SqlObjectClassDefinition ocDef : schema.objectClasses()) {
            SqlObjectClassMapping mapping = build(ocDef, metadataFactory);
            if (mapping != null) {
                result.put(ocDef.objectClass(), mapping);
            }
        }
        return result;
    }

    /**
     * Builds a mapping for a single ConnId object class.
     *
     * @return the mapping, or null if the object class is not backed by a SQL table
     */
    static SqlObjectClassMapping build(SqlObjectClassDefinition ocDef,
                                      SqlQuerydslMetadataFactory metadataFactory) {
        String locator = ocDef.locator();
        if (locator == null || locator.isEmpty()) {
            return null;
        }

        QueryDSLMetadata metadata = metadataFactory.getMetadata(locator);
        if (metadata == null) {
            return null;
        }

        // Find UID column mapping from ConnId attributes
        String uidSqlColumn = null;
        for (BaseAttributeDefinition attr : ocDef.attributes()) {
            String connIdName = attr.connId().getName();
            if (Uid.NAME.equals(connIdName)) {
                String remoteName = attr.remoteName();
                if (remoteName != null && !remoteName.isEmpty()) {
                    uidSqlColumn = remoteName;
                }
                break;
            }
        }

        // Build attribute mappings from ConnId attributes (using native/protocol name = SQL column)
        List<SqlObjectClassMapping.SqlAttributeMapping> mappings = new ArrayList<>();
        Map<String, BaseAttributeDefinition> attrMap = new HashMap<>();

        for (BaseAttributeDefinition attr : ocDef.attributes()) {
            String remoteName = attr.remoteName();
            if (remoteName != null) {
                attrMap.put(remoteName, attr);
            }
        }

        for (QueryDSLMetadata.ColumnMeta colMeta : metadata.getColumns().values()) {
            String sqlCol = colMeta.getName();
            BaseAttributeDefinition attrDef = attrMap.get(sqlCol);
            if (attrDef == null) {
                continue;
            }

            String connIdName = attrDef.connId().getName();
            boolean returnedByDefault = attrDef.connId().isReturnedByDefault();
            mappings.add(new SqlObjectClassMapping.SqlAttributeMapping(
                    connIdName, sqlCol, returnedByDefault));
        }

        // Find UID column if not already found from ConnId UID attribute
        if (uidSqlColumn == null) {
            for (QueryDSLMetadata.ColumnMeta colMeta : metadata.getColumns().values()) {
                if (colMeta.isPrimaryKey()) {
                    uidSqlColumn = colMeta.getName();
                    break;
                }
            }
        }

        if (uidSqlColumn == null && !metadata.getColumns().isEmpty()) {
            uidSqlColumn = new ArrayList<>(metadata.getColumns().keySet()).getFirst();
        }

        // Collect column names for returnedByDefault attributes
        List<String> returnedByDefaultColumns = new ArrayList<>();
        for (SqlObjectClassMapping.SqlAttributeMapping m : mappings) {
            if (m.isReturnedByDefault()) {
                returnedByDefaultColumns.add(m.getSqlColumn());
            }
        }

        if (mappings.isEmpty()) {
            return null;
        }

        return new SqlObjectClassMapping(
                ocDef.objectClass(),
                locator,
                mappings,
                uidSqlColumn,
                returnedByDefaultColumns
        );
    }

}