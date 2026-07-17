/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.filter.*;

import java.util.function.BinaryOperator;

/**
 * Translates ConnId {@link Filter filters} into QueryDSL predicates for SQL-based search.
 *
 * <p>For each filter operand:
 * <ul>
 *   <li>Resolves the ConnId attribute name to an {@link SqlAttributeDefinition} via the object class.</li>
 *   <li>Obtains the typed QueryDSL path via {@link SqlAttributeDefinition#sql()} and
 *       {@link com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping#dslPath(Path)}.</li>
 *   <li>Coerces the attribute value to the SQL wire type via
 *       {@link com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping#toSqlValue(Object)}.</li>
 *   <li>Applies a specific QueryDSL operator (eq, like, gt, lt, …).</li>
 * </ul>
 * Compound filters ({@link AndFilter}, {@link OrFilter}, {@link NotFilter}) are handled recursively.
 *
 * @since 0.1
 */
public final class SqlFilterTranslator {

    private final SqlObjectClassDefinition              objectClass;
    private final RelationalPathBase<?> tablePath;

    SqlFilterTranslator(SqlObjectClassDefinition objectClass,
                        RelationalPathBase<?> tablePath) {
        this.objectClass  = objectClass;
        this.tablePath    = tablePath;
    }

    /**
     * Returns a QueryDSL predicate for the given filter, or {@code null} if the filter is
     * {@code null}.
     */
    public static Predicate translate(SqlObjectClassDefinition objectClass,
                                      RelationalPathBase<?> tablePath,
                                      Filter filter) {
        if (filter == null) {
            return null;
        }
        return new SqlFilterTranslator(objectClass, tablePath).build(filter);
    }

    public static ConnectorException unsupportedFilterException(Filter filter) {
        return new ConnectorException("Unsupported filter: " + filter);
    }

    // ──────────────────────────────────────────────────────────────────────

    private BooleanExpression build(Filter filter) {
        if (filter instanceof AndFilter cf) {
            return buildCompound(cf, BooleanExpression::and);
        }
        if (filter instanceof OrFilter cf) {
            return buildCompound(cf, BooleanExpression::or);
        }
        if (filter instanceof NotFilter nt) {
            var child = nt.getFilter();
            if (child == null) {
                return Expressions.TRUE;
            }
            return negate(build(child));
        }
        if (filter instanceof AttributeFilter singleValue) {
            return buildValueFilter(singleValue);
        }

        return Expressions.TRUE;
    }

    private BooleanExpression buildValueFilter(AttributeFilter filter) {
        var attrDef = objectClass.attributeFromConnIdName(filter.getAttribute().getName());
        if (attrDef == null || attrDef.sql() == null) {
            // TODO: Maybe better exception -> eg. attrDef == null -> unknown attribute
            throw SqlFilterTranslator.unsupportedFilterException(filter);
        }
        var filterProcessor = attrDef.sql().sqlFilter();
        if (filterProcessor == null) {
            throw unsupportedFilterException(filter);
        }
        return filterProcessor.predicateFor(tablePath, filter);
    }

    // ── Compound (AND/OR) ──────────────────────────────────────────────────

    private BooleanExpression buildCompound(CompositeFilter filter,
                                            BinaryOperator<BooleanExpression> op) {
        BooleanExpression accumulator = null;
        for (Filter f : filter.getFilters()) {
            var expr = build(f);
            if (expr == null || expr == Expressions.TRUE) {
                continue;
            }
            accumulator = accumulator == null ? expr : op.apply(accumulator, expr);
        }
        return accumulator == null ? Expressions.TRUE : accumulator;
    }

    private BooleanExpression negate(BooleanExpression predicate) {
        if (predicate == null || predicate == Expressions.TRUE) {
            return Expressions.FALSE;
        }
        return predicate.not();
    }

    public static String escapeValue(String value) {
        if (value == null) {
            return "";
        }
        int len = value.length();
        var sb = new StringBuilder(len + 4);
        for (int i = 0; i < len; i++) {
            char c = value.charAt(i);
            switch (c) {
            case '\\': sb.append("\\\\"); break;
            case '%':  sb.append("\\%");  break;
            case '_':  sb.append("\\_");  break;
            default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }
}
