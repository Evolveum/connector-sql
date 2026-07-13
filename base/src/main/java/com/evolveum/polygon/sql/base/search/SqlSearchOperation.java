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
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.evolveum.polygon.sql.base.objectclass.SqlObjectClassMapping;
import com.evolveum.polygon.sql.base.schema.QueryDSLMetadata;
import com.evolveum.polygon.sql.base.schema.SqlQuerydslMetadataFactory;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * QueryDSL-based search operation for SQL object classes.
 *
 */
public class SqlSearchOperation implements ObjectSearchOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final QueryDSLMetadata querydslMetadata;

    public SqlSearchOperation(SqlBaseContext context, SqlQuerydslMetadataFactory metadataFactory, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
        String tableName = objectClass.sql().getTableName();
        this.querydslMetadata = tableName != null ? metadataFactory.getMetadata(tableName) : null;
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler,
                             OperationOptions options) {
        var selectedColumns = selectColumns(options);

        try (var conn = context.getConnection()) {

            // Check filter support - throw UnsupportedOperationException if filter is non-null
            if (filter != null) {
                throw new UnsupportedOperationException("Filter support not yet implemented, got: " + filter);
            }

            var jdbcConn = conn.getConnection();
            int pageSize = 200;
            int offset = 0;

            while (true) {
                List<Map<String, Object>> rows;
                try {
                    rows = context.getSqlQueryEngine().select(
                            jdbcConn,
                            objectClass.sql().getTableName(),
                            querydslMetadata,
                            selectedColumns,
                            null,  // predicate (none)
                            null,  // order by (none)
                            pageSize,
                            offset
                    );

                    for (Map<String, Object> row : rows) {
                        var obj = buildConnectorObject(row);
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

    private List<String> selectColumns(OperationOptions options) {
        return objectClass.attributes().stream().filter(a -> a.sql() != null)
                .filter(a -> a.connId().isReturnedByDefault())
                .map(a -> a.sql().column().value())
                .toList();
    }

    private ConnectorObject buildConnectorObject(Map<String, Object> row) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass.objectClass());
        for (var attr : objectClass.attributes()) {
                var mapping = attr.sql();
                if (mapping != null) {
                    var value = mapping.valuesFromObject(row);
                    if (value != null) {
                        builder.addAttribute(attr.attributeOf(value));
                    }
                }
        }
        return builder.build();
    }
}