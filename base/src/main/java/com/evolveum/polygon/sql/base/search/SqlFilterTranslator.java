/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.types.ConstantImpl;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.*;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.objects.filter.*;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.function.BiFunction;
import java.util.function.Function;

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

    // ──────────────────────────────────────────────────────────────────────

    private Predicate build(Filter filter) {
        if (filter instanceof EqualsFilter af) {
            return buildEquals(af);
        }
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
        if (filter instanceof ContainsFilter sf) {
            return buildLike(sf, v -> "%" + v + "%");
        }
        if (filter instanceof StartsWithFilter sf) {
            return buildLike(sf, v -> v + "%");
        }
        if (filter instanceof EndsWithFilter sf) {
            return buildLike(sf, v -> "%" + v);
        }
        if (filter instanceof GreaterThanFilter gf) {
            return buildGt(gf);
        }
        if (filter instanceof GreaterThanOrEqualFilter gf) {
            return buildGe(gf);
        }
        if (filter instanceof LessThanFilter lf) {
            return buildLt(lf);
        }
        if (filter instanceof LessThanOrEqualFilter lf) {
            return buildLe(lf);
        }
        return Expressions.TRUE;
    }

    // ── Compound (AND/OR) ──────────────────────────────────────────────────

    private BooleanExpression buildCompound(CompositeFilter filter,
                                            BiFunction<BooleanExpression, BooleanExpression, BooleanExpression> op) {
        BooleanExpression acc = null;
        for (Filter f : filter.getFilters()) {
            var pred = build(f);
            if (pred == null || pred == Expressions.TRUE) {
                continue;
            }
            BooleanExpression expr = toBoolean(pred);
            acc = acc == null ? expr : op.apply(acc, expr);
        }
        return acc == null ? Expressions.TRUE : acc;
    }

    private BooleanExpression negate(Predicate predicate) {
        if (predicate == null || predicate == Expressions.TRUE) {
            return Expressions.FALSE;
        }
        return toBoolean(predicate).not();
    }

    // ── EQUALS ─────────────────────────────────────────────────────────────────────────────────

    private BooleanExpression buildEquals(EqualsFilter filter) {
        var attrName = filter.getName();
        Path<?> rawPath = resolvePath(attrName);
        Object value = getFirstValue(filter);
        if (value == null) {
            return isNull(rawPath);
        }
        var sqlValue = toSqlValue(attrName, value);
        return eqTyped(rawPath, sqlValue);
    }

    // ── LIKE (contains, startsWith, endsWith) ─────────────────────────────────────────────────

    private BooleanExpression buildLike(AttributeFilter filter,
                                         Function<String, String> patternFn) {
        var attrName = filter.getName();
        Path<?> rawPath = resolvePath(attrName);
        var value = getFilterValue(filter);
        if (value == null) {
            return isNull(rawPath);
        }
        if (!(rawPath instanceof StringPath sp)) {
            return Expressions.TRUE;
        }
        return sp.like(patternFn.apply(escapeValue(value)));
    }

    // ── Numeric comparison (gt, ge, lt, le) ─────────────────────────────────────────────────

    private BooleanExpression buildGt(GreaterThanFilter filter) {
        return compareNumeric(filter, (n, v) -> compareGt(n, v));
    }

    private BooleanExpression compareGt(NumberPath<?> np, Number v) {
        if (np.getType() == Integer.class) {
            return np.gt(v.intValue());
        }
        if (np.getType() == Long.class) {
            return np.gt(v.longValue());
        }
        if (np.getType() == Double.class) {
            return np.gt(v.doubleValue());
        }
        if (np.getType() == Float.class) {
            return np.gt(v.floatValue());
        }
        if (np.getType() == Short.class) {
            return np.gt(v.shortValue());
        }
        if (np.getType() == Byte.class) {
            return np.gt(v.byteValue());
        }
        return np.gt(v.doubleValue());
    }

    private BooleanExpression buildGe(GreaterThanOrEqualFilter filter) {
        return compareNumeric(filter, (n, v) -> compareGe(n, v));
    }

    private BooleanExpression compareGe(NumberPath<?> np, Number v) {
        if (np.getType() == Integer.class) {
            return np.goe(v.intValue());
        }
        if (np.getType() == Long.class) {
            return np.goe(v.longValue());
        }
        if (np.getType() == Double.class) {
            return np.goe(v.doubleValue());
        }
        if (np.getType() == Float.class) {
            return np.goe(v.floatValue());
        }
        if (np.getType() == Short.class) {
            return np.goe(v.shortValue());
        }
        if (np.getType() == Byte.class) {
            return np.goe(v.byteValue());
        }
        return np.goe(v.doubleValue());
    }

    private BooleanExpression buildLt(LessThanFilter filter) {
        return compareNumeric(filter, (n, v) -> compareLt(n, v));
    }

    private BooleanExpression compareLt(NumberPath<?> np, Number v) {
        if (np.getType() == Integer.class) {
            return np.lt(v.intValue());
        }
        if (np.getType() == Long.class) {
            return np.lt(v.longValue());
        }
        if (np.getType() == Double.class) {
            return np.lt(v.doubleValue());
        }
        if (np.getType() == Float.class) {
            return np.lt(v.floatValue());
        }
        if (np.getType() == Short.class) {
            return np.lt(v.shortValue());
        }
        if (np.getType() == Byte.class) {
            return np.lt(v.byteValue());
        }
        return np.lt(v.doubleValue());
    }

    private BooleanExpression buildLe(LessThanOrEqualFilter filter) {
        return compareNumeric(filter, (n, v) -> compareLe(n, v));
    }

    private BooleanExpression compareLe(NumberPath<?> np, Number v) {
        if (np.getType() == Integer.class) {
            return np.loe(v.intValue());
        }
        if (np.getType() == Long.class) {
            return np.loe(v.longValue());
        }
        if (np.getType() == Double.class) {
            return np.loe(v.doubleValue());
        }
        if (np.getType() == Float.class) {
            return np.loe(v.floatValue());
        }
        if (np.getType() == Short.class) {
            return np.loe(v.shortValue());
        }
        if (np.getType() == Byte.class) {
            return np.loe(v.byteValue());
        }
        return np.loe(v.doubleValue());
    }

    private BooleanExpression compareNumeric(AttributeFilter filter,
                                              BiFunction<NumberPath<?>, Number, BooleanExpression> op) {
        var attrName = filter.getName();
        Path<?> rawPath = resolvePath(attrName);
        if (!(rawPath instanceof NumberPath<?> np)) {
            return Expressions.TRUE;
        }
        var value = toNumber(filter);
        if (value == null) {
            return isNull(rawPath);
        }
        return op.apply(np, value);
    }

    // ── Attribute / path resolution ─────────────────────────────────────────────────────────

    private Path<?> resolvePath(String attrName) {
        var def = objectClass.attributeFromConnIdName(attrName);
        if (def == null) {
            throw new IllegalArgumentException("Attribute " + attrName + " not found");
        }
        return def.sql().dslPath(tablePath);
    }

    // ── Value extraction & coercion ─────────────────────────────────────────────────────────

    private static Object getFirstValue(AttributeFilter filter) {
        if (filter == null || filter.getAttribute() == null) {
            return null;
        }
        var values = filter.getAttribute().getValue();
        return values.isEmpty() ? null : values.getFirst();
    }

    private String getFilterValue(AttributeFilter filter) {
        Object value = getFirstValue(filter);
        return value != null ? value.toString() : null;
    }

    private Number toNumber(AttributeFilter filter) {
        Object value = getFirstValue(filter);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof String s) {
            try {
                NumberFormat nf = NumberFormat.getInstance();
                return nf.parse(s);
            } catch (ParseException e) {
                return null;
            }
        }
        return null;
    }

    private Object toSqlValue(String attrName, Object value) {
        for (SqlAttributeDefinition def : objectClass.attributes()) {
            if (def.connId().getName().equals(attrName)) {
                if (def.sql() != null) {
                    return def.sql().toSqlValue(value);
                }
            }
        }
        return value;
    }

    // ── QueryDSL predicate builders ─────────────────────────────────────────────────────────

    private BooleanExpression isNull(Path<?> path) {
        if (path instanceof StringPath sp) {
            return sp.isNull();
        }
        if (path instanceof BooleanPath bp) {
            return bp.isNull();
        }
        if (path instanceof NumberPath<?> np) {
            return np.isNull();
        }
        if (path instanceof DateTimePath<?> dp) {
            return dp.isNull();
        }
        if (path instanceof ComparableExpression<?> cp) {
            return cp.isNull();
        }
        return Expressions.TRUE;
    }

    private BooleanExpression eqTyped(Path<?> raw, Object value) {
        if (raw instanceof StringPath sp && value instanceof String s) {
            return sp.eq(s);
        }
        if (raw instanceof BooleanPath bp && value instanceof Boolean b) {
            return bp.eq(b);
        }
        if (raw instanceof NumberPath<?> np && value instanceof Number n) {
            // Use Expression.constant to wrap the value for type-safe comparison
            return np.eq(ConstantImpl.create(n));
        }
        if (raw instanceof DateTimePath<?> dp) {
            return dp.eq(ConstantImpl.create(value));
        }
        if (raw instanceof DatePath<?> dp) {
            return dp.eq(ConstantImpl.create(value));
        }
        if (raw instanceof TimePath<?> tp) {
            return tp.eq(ConstantImpl.create(value));
        }
        if (raw instanceof ComparableExpression<?> ce) {
            return ce.eq(ConstantImpl.create(value));
        }
        return Expressions.FALSE;
    }

    private static BooleanExpression toBoolean(Predicate predicate) {
        if (predicate instanceof BooleanExpression be) {
            return be;
        }
        return Expressions.TRUE;
    }

    private static String escapeValue(String value) {
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