/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.objectclass.SqlObjectClassMapping;
import com.evolveum.polygon.sql.base.schema.QueryDSLMetadata;
import com.evolveum.polygon.sql.base.schema.SqlQuerydslMetadataFactory;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * QueryDSL-based search operation for SQL object classes.
 *
 * <p>Maps SQL {@link QueryDSLMetadata} to ConnId {@link ConnectorObject}s via
 * {@link SqlObjectClassMapping} instances built by
 * {@link com.evolveum.polygon.sql.base.objectclass.SqlObjectClassMapper}.</p>
 */
public class SqlSearchOperation implements ObjectSearchOperation {

    private final SqlBaseContext context;
    private final SqlQuerydslMetadataFactory metadataFactory;
    private final ObjectClass objectClass;
    private SqlObjectClassMapping mapping;
    private QueryDSLMetadata querydslMetadata;

    public SqlSearchOperation(SqlBaseContext context, SqlQuerydslMetadataFactory metadataFactory, ObjectClass objectClass) {
        this.context = context;
        this.metadataFactory = metadataFactory;
        this.objectClass = objectClass;
        String tableName = null;
        for (Map.Entry<ObjectClass, SqlObjectClassMapping> entry : context.getObjectClassMappings().entrySet()) {
            if (entry.getKey() != null && entry.getKey().getObjectClassValue()
                    .equalsIgnoreCase(objectClass.getObjectClassValue())) {
                tableName = entry.getValue().getTableName();
                this.mapping = entry.getValue();
                break;
            }
        }
        this.querydslMetadata = tableName != null ? metadataFactory.getMetadata(tableName) : null;
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler,
                              OperationOptions options) {
        List<String> selectedColumns = null;

        if (mapping == null || querydslMetadata == null) {
            return;
        }

        selectedColumns = mapping.getReturnedByDefaultColumnNames();

        try (SqlConnection conn = context.getConnection()) {

            // Check filter support - throw UnsupportedOperationException if filter is non-null
            if (filter != null) {
                throw new UnsupportedOperationException("Filter support not yet implemented, got: " + filter);
            }

            Connection jdbcConn = conn.getConnection();
            int pageSize = 200;
            int offset = 0;

            while (true) {
                List<Map<String, Object>> rows;
                try {
                    rows = context.getSqlQueryEngine().select(
                            jdbcConn,
                            mapping.getTableName(),
                            querydslMetadata,
                            selectedColumns,
                            null,  // predicate (none)
                            null,  // order by (none)
                            pageSize,
                            offset
                    );

                    for (Map<String, Object> row : rows) {
                        ConnectorObject obj = buildConnectorObject(row, mapping);
                        if (!resultsHandler.handle(obj)) {
                            return;
                        }
                    }

                    if (rows.isEmpty() || rows.size() < pageSize) {
                        return;
                    }

                    offset += pageSize;
                } catch (SQLException e) {
                    throw new org.identityconnectors.framework.common.exceptions.ConnectorException(
                            "QueryDSL select failed: " + e.getMessage(), e);
                }
            }
        }
    }

private ConnectorObject buildConnectorObject(Map<String, Object> row,
                                                   SqlObjectClassMapping mapping) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass);

        String uidValue = mapping.getUidValueStr(row);
        if (uidValue != null) {
            builder.setUid(uidValue);
            builder.setName(new Name(uidValue));
        }

        for (SqlObjectClassMapping.SqlAttributeMapping attr : mapping.getAttributeMappings()) {
            if (attr.isReturnedByDefault()) {
                String connIdName = attr.getConnIdName();
                Object value = row.get(attr.getSqlColumn());
                if (value != null) {
                    // __UID__ attribute must be String, not Long/Integer/etc.
                    if (Uid.NAME.equals(connIdName)) {
                        value = String.valueOf(value);
                    }
                    builder.addAttribute(AttributeBuilder.build(connIdName, value));
                }
            }
        }

        return builder.build();
    }
}