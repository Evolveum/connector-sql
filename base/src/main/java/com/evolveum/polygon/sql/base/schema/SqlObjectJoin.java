/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.schema;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.RelationalPathBase;

/** A resolved, read-only LEFT JOIN contributing flat attributes to its root object. */
public record SqlObjectJoin(SqlTableInfo table, RelationalPathBase<?> path, BooleanExpression condition) {
}
