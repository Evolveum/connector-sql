/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLTemplates;
import org.jetbrains.annotations.UnknownNullability;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

/**
 * QueryDSL-based SQL query engine for SELECT operations.
 * Uses runtime {@link com.querydsl.core.types.dsl.PathBuilder}
 * for dynamic column resolution without compile-time metamodel generation.
 */
public class SqlQueryEngine {

    private final SQLTemplates templates;

    public SqlQueryEngine(SQLTemplates templates) {
        this.templates = templates;
    }

    /**
     * Executes a SELECT using only the specified columns (typically returned-by-default).
     * Each row is returned as a flat map keyed by the original SQL column name.
     */
    public List<Tuple> select(
            Connection conn, RelationalPathBase<?> entityPath,
            @UnknownNullability Collection<Path<?>> selectedColumns,
            Predicate predicate, List<Path<?>> orderBys,
            int pageSize, int pageOffset) throws SQLException {

        SQLQuery<?> query =
                new SQLQuery<>(conn, templates);

        // Use the actual table name from metadata for correct case handling
        // Build list of select expressions (type-safe with correct path types)
        query.select(selectedColumns.toArray(new Path[0]));
        query.from(entityPath);

        if (predicate != null) {
            query.where(predicate);
        }

        // ORDER BY
        if (orderBys != null && !orderBys.isEmpty()) {
            for (Path<?> path : orderBys) {
                if (path instanceof ComparablePath<?> cmp) {
                    query.orderBy(cmp.asc());
                }
            }
        }

        query.limit(pageSize).offset(pageOffset);

        return (List<Tuple>) (List) query.fetch();
    }

}
