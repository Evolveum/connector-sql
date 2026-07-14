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
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.filter.Filter;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * QueryDSL-based search operation for SQL object classes.
 *
 */
public class SqlSearchOperation implements ObjectSearchOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;

    public SqlSearchOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    private RelationalPathBase<?> getTablePath() {
        return objectClass.sql().pathAlias("o");
    }

    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler,
                              OperationOptions options) {

        var tablePath = objectClass.sql().pathAlias("o");
        var selectedColumns = selectColumns(tablePath, options);

        try (var conn = context.getConnection()) {

            var jdbcConn = conn.getConnection();
            int pageSize = 200;
            int offset = 0;

            while (true) {
                List<Tuple> rows;
                try {
                    Predicate predicate = null;
                    if (filter != null) {
                        predicate = SqlFilterTranslator.translate(objectClass, tablePath, filter);
                    }

                    rows = context.getSqlQueryEngine().select(jdbcConn, tablePath, selectedColumns.values(),
                            predicate,
                            null,  // order by (none)
                            pageSize,
                            offset
                    );

                    for (var row : rows) {
                        var obj = buildConnectorObject(row, selectedColumns);
                        if (!resultsHandler.handle(obj)) {
                            return;
                        }
                    }

                    if (rows.isEmpty() || rows.size() < pageSize) {
                        return;
                    }

                    offset += pageSize;
                } catch (SQLException e) {
                    throw new ConnectorException(
                            "QueryDSL select failed: " + e.getMessage(), e);
                }
            }
        }
    }

    private Map<SqlAttributeDefinition, Path<?>> selectColumns(Path<?> table, OperationOptions options) {
        Map<SqlAttributeDefinition, Path<?>> columns = new HashMap<>();
        for (var attr : objectClass.attributes()) {
            if (attr.sql() != null && attr.connId().isReturnedByDefault()) {
                columns.put(attr, attr.sql().dslPath(table));
            }
        }
        return columns;
    }

    private ConnectorObject buildConnectorObject(Tuple row, Map<SqlAttributeDefinition, Path<?>> attributes) {
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass.objectClass());
        for (var attrEntry : attributes.entrySet()) {
            var attr = attrEntry.getKey();
            var mapping = attr.sql();
                if (mapping != null) {
                    var value = mapping.valuesFromAttribute(row.get(attrEntry.getValue()));
                    builder.addAttribute(attr.attributeOf(value));
                }
        }
        return builder.build();
    }
}