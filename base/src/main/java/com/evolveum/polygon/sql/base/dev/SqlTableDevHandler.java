/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.dev;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Search handler that exposes raw JDBC table and column metadata in development mode. */
public class SqlTableDevHandler implements ObjectSearchOperation {

    private static final ObjectClass OBJECT_CLASS = new ObjectClass(SqlDevelopmentMode.TABLE_OC_NAME);
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final SqlBaseContext context;

    public SqlTableDevHandler(SqlBaseContext context) {
        this.context = context;
    }

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler,
            OperationOptions operationOptions) {
        for (var table : context.getDetectedTables()) {
            var uid = tableUid(table);
            if (!matchesFilter(filter, table, uid)) {
                continue;
            }
            if (!resultsHandler.handle(createTableObject(table, uid))) {
                return;
            }
        }
    }

    private static ConnectorObject createTableObject(SqlTableInfo table, String uid) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(OBJECT_CLASS);
        builder.setUid(uid);
        builder.setName(table.getName());
        addIfPresent(builder, SqlDevelopmentMode.CATALOG_ATTRIBUTE, table.getCatalog());
        addIfPresent(builder, SqlDevelopmentMode.SCHEMA_ATTRIBUTE, table.getSchema());
        addIfPresent(builder, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE, table.getTableType());
        addIfPresent(builder, SqlDevelopmentMode.REMARKS_ATTRIBUTE, table.getRemarks());
        builder.addAttribute(SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE, tableToJson(table));
        return builder.build();
    }

    private static void addIfPresent(ConnectorObjectBuilder builder, String name, String value) {
        if (value != null) {
            builder.addAttribute(name, value);
        }
    }

    private static boolean matchesFilter(Filter filter, SqlTableInfo table, String uid) {
        if (filter == null) {
            return true;
        }
        if (filter instanceof EqualsFilter equalsFilter) {
            var attributeName = equalsFilter.getAttribute().getName();
            var filterValue = AttributeUtil.getSingleValue(equalsFilter.getAttribute());
            if (Uid.NAME.equals(attributeName)) {
                return Objects.equals(uid, filterValue);
            }
            if (Name.NAME.equals(attributeName)) {
                return Objects.equals(table.getName(), filterValue);
            }
            if (SqlDevelopmentMode.SCHEMA_ATTRIBUTE.equals(attributeName)) {
                return Objects.equals(table.getSchema(), filterValue);
            }
        }
        return false;
    }

    private static String tableUid(SqlTableInfo table) {
        return Stream.of(table.getCatalog(), table.getSchema(), table.getName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("."));
    }

    private static String tableToJson(SqlTableInfo table) {
        var content = new LinkedHashMap<String, Object>();
        content.put("catalog", table.getCatalog());
        content.put("schema", table.getSchema());
        content.put("name", table.getName());
        content.put("tableType", table.getTableType());
        content.put("remarks", table.getRemarks());
        content.put("columns", table.getColumns().stream().map(SqlTableDevHandler::columnContent).toList());
        try {
            return OBJECT_MAPPER.writeValueAsString(content);
        } catch (Exception e) {
            throw new ConnectorException("Failed to serialize SQL table metadata", e);
        }
    }

    private static Map<String, Object> columnContent(SqlColumnMeta column) {
        var content = new LinkedHashMap<String, Object>();
        content.put("name", column.getName());
        content.put("typeName", column.getTypeName());
        content.put("typeCode", column.getTypeCode());
        content.put("size", column.getSize());
        content.put("javaType", column.getJavaType() != null ? column.getJavaType().getTypeName() : null);
        content.put("nullable", column.isNullable());
        content.put("primaryKey", column.isPrimaryKey());
        content.put("unique", column.isUnique());
        content.put("defaultValue", column.getDefaultValue());
        content.put("autoIncrement", column.isAutoIncrement());
        content.put("referencedTable", column.getReferencedTable());
        content.put("referencedColumn", column.getReferencedColumn());
        content.put("foreignKeyName", column.getForeignKeyName());
        return content;
    }
}
