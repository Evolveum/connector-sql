/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.sql.RelationalPathBase;

/** Explicit column-to-column equality predicates; multiple calls are combined with AND. */
public final class SqlJoinPredicateBuilder {
    private final Table left;
    private final Table right;
    private BooleanExpression predicate;

    public SqlJoinPredicateBuilder(SqlTableInfo leftTable, RelationalPathBase<?> leftPath,
                                   SqlTableInfo rightTable, RelationalPathBase<?> rightPath) {
        left = new Table(leftTable, leftPath);
        right = new Table(rightTable, rightPath);
    }

    public Table left() { return left; }
    public Table right() { return right; }

    public BooleanExpression build() {
        if (predicate == null) {
            throw new IllegalArgumentException("Join requires at least one left/right column equality");
        }
        return predicate;
    }

    public final class Table {
        private final SqlTableInfo metadata;
        private final RelationalPathBase<?> path;

        private Table(SqlTableInfo metadata, RelationalPathBase<?> path) {
            this.metadata = metadata;
            this.path = path;
        }

        public Column column(String name) {
            var column = metadata.getColumns().stream()
                    .filter(candidate -> candidate.getName().equalsIgnoreCase(name))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "Column " + name + " not found in join table " + metadata.getName()));
            if (column.getValueMapping() == null
                    || !(column.getValueMapping().pathFor(path, column.getName())
                    instanceof SimpleExpression<?> expression)) {
                throw new IllegalArgumentException("Unsupported join column: " + name);
            }
            return new Column(this, expression);
        }
    }

    public final class Column {
        private final Table table;
        private final SimpleExpression<?> path;

        private Column(Table table, SimpleExpression<?> path) {
            this.table = table;
            this.path = path;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        public SqlJoinPredicateBuilder eq(Column other) {
            if (table == other.table) {
                throw new IllegalArgumentException("Join equality must compare left and right columns");
            }
            var equality = ((SimpleExpression) path).eq(other.path);
            predicate = predicate == null ? equality : predicate.and(equality);
            return SqlJoinPredicateBuilder.this;
        }
    }
}
