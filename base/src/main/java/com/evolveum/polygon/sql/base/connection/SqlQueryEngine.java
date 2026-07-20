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
import com.querydsl.core.types.dsl.BooleanExpression;
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
 * QueryDSL-based SQL query engine for SELECT and sync operations.
 */
public class SqlQueryEngine {

    private final SQLTemplates templates;

    public SqlQueryEngine(SQLTemplates templates) {
        this.templates = templates;
    }


    /**
     * SELECT ... FROM table WHERE syncCol > syncPoint ORDER BY syncCol ASC LIMIT/OFFSET.
     */
    public List<Tuple> selectRange(Connection conn, RelationalPathBase<?> path,
                                    Collection<Path<?>> columns, ComparablePath<?> syncColumn,
                                    Object syncPoint,
                                    BooleanExpression extraFilter,
                                    int pageSize, int offset) {
        SQLQuery<?> query = new SQLQuery<>(conn, templates);
        query.select(columns.toArray(new Path[0]));
        query.from(path);
        if (syncPoint != null) {
            @SuppressWarnings("unchecked")
            ComparablePath<Long> cp = (ComparablePath<Long>) syncColumn;
            query.where(cp.gt(((Number) syncPoint).longValue()));
        }
        if (extraFilter != null) query.where(extraFilter);
        query.orderBy(syncColumn.asc());
        query.limit(pageSize).offset(offset);
        return (List<Tuple>) (List) query.fetch();
    }

    /**
     * SELECT uid, syncCol FROM table WHERE deleteFilter ORDER BY syncCol.
     */
    public List<Tuple> selectTombstones(Connection conn, RelationalPathBase<?> path,
                                         Path<?> uidColumn, ComparablePath<?> syncColumn,
                                         BooleanExpression deleteFilter,
                                         int pageSize) {
        return new SQLQuery<>(conn, templates)
                .select(uidColumn, syncColumn)
                .from(path)
                .where(deleteFilter)
                .orderBy(syncColumn.asc())
                .limit(pageSize)
                .fetch();
    }

}
