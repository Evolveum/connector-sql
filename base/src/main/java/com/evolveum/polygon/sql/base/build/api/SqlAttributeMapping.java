/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.spi.AttributeProtocolMapping;
import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.evolveum.polygon.sql.base.SqlTuple;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;
import com.evolveum.polygon.sql.base.search.SqlFilterTranslator;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.*;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.objects.filter.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL protocol mapping that describes how a column value maps to a ConnId attribute.
 * Acts as the SQL equivalent of {@code JsonAttributeMapping}.
 *
 * <p>Implements {@link AttributeProtocolMapping} with:
 * <ul>
 *   <li>{@code O} = SQL row representation ({@code Map<String, Object>})</li>
 *   <li>{@code A} = SQL column value ({@code Object})</li>
 * </ul>
 *
 * <p>The mapping uses a {@link SqlSchemaValueMapping} to coerce raw JDBC values
 * to the appropriate ConnId wire type.</p>
 */
public interface SqlAttributeMapping extends AttributeProtocolMapping<SqlTuple, Object> {

    static SingleColumn singleColumn(DefinitionValue<String> column, SqlValueMapping sqlMapping, ValueMapping<Object, Object> valueMapping) {
        return new SingleColumn(column, sqlMapping, valueMapping);
    }

    static MultiColumn multiColumn(SingleColumn mainColumn, List<SingleColumn> additionalColumns, String delimiter) {
        return new MultiColumn(mainColumn, additionalColumns, delimiter);
    }

    static MultiColumn multiColumn(SingleColumn mainColumn, List<SingleColumn> additionalColumns) {
        return new MultiColumn(mainColumn, additionalColumns, DEFAULT_DELIMITER);
    }

    String DEFAULT_DELIMITER = ".";

    default FilterSupport sqlFilter() {
        if (this instanceof MultiColumn mc) { return mc.sqlFilter(); }
        return null;
    }

    Collection<Path<?>> selectPaths(Path<?> table);

    DefinitionValue<String> column();

    interface FilterSupport {

        BooleanExpression predicateFor(RelationalPathBase<?> tablePath, AttributeFilter filter);

        BooleanExpression predicateFor(RelationalPathBase<?> tablePath, SingleValueAttributeFilter filter);

    }

    record SingleColumn(DefinitionValue<String> column, SqlValueMapping sqlMapping,
                        ValueMapping<Object, Object> valueMapping) implements SqlAttributeMapping {

        /**
         * Extracts the column value from the SQL row.
         */
        @Override
        public Object attributeFromObject(SqlTuple row) {
            return row.get(dslPath(row.table()));
        }

        /**
         * Returns the ConnId Java type for this attribute.
         * Derived from the SqlSchemaValueMapping's connIdClass.
         */
        @Override
        public Class<?> connIdType() {
            return valueMapping.connIdType();
        }

        /**
         * Converts a single column value to the ConnId wire type.
         */
        @Override
        public Object singleValueFromAttribute(Object columnValue) {
            if (columnValue == null) {
                return null;
            }
            return valueMapping.toConnIdValue(columnValue);
        }

        /**
         * Returns a list of column values. For SQL columns (non-multi-valued),
         * this returns a single-element list or empty list.
         */
        @Override
        public List<Object> valuesFromAttribute(Object columnValue) {
            if (columnValue == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(singleValueFromAttribute(columnValue));
        }

        public Path<?> dslPath(Path<?> parent) {
            if (sqlMapping instanceof SqlValueMapping.SingleColumn single) {
                return single.pathFor(parent, column.value());
            }
            return Expressions.path(Object.class, parent, column.value());
        }

        @Override
        public FilterSupport sqlFilter() {
            return new FilterSupport() {

                @Override
                public BooleanExpression predicateFor(RelationalPathBase<?> tablePath, AttributeFilter filter) {
                    var rawPath = dslPath(tablePath);
                    if (filter instanceof EqualsFilter && rawPath instanceof SimpleExpression expression) {
                        var connIdValue = filter.getAttribute().getValue();
                        if (connIdValue.isEmpty()) {
                            return expression.isNull();
                        }
                        return expression.eq(toSqlValue(connIdValue.getFirst()));
                    }
                    if (filter instanceof SingleValueAttributeFilter singleValue) {
                        return predicateFor(tablePath, singleValue);
                    }
                    throw SqlFilterTranslator.unsupportedFilterException(filter);
                }

                @Override
                public BooleanExpression predicateFor(RelationalPathBase<?> tablePath, SingleValueAttributeFilter filter) {
                    var sqlValue = toSqlValue(filter.getValue());
                    var rawPath = dslPath(tablePath);

                    if (rawPath instanceof StringPath attrPath) {
                        // FIXME: Maybe escape sqlValue
                        if (filter instanceof ContainsFilter sf) {
                            return attrPath.like("%" + sqlValue + "%");
                        }
                        if (filter instanceof StartsWithFilter sf) {
                            return attrPath.like(sqlValue + "%");
                        }
                        if (filter instanceof EndsWithFilter sf) {
                            return attrPath.like("%" + sqlValue);
                        }
                    }
                    if (rawPath instanceof NumberPath attrPath && sqlValue instanceof Number value) {
                        if (filter instanceof GreaterThanFilter gf) {
                            return attrPath.gt(value);
                        }
                        if (filter instanceof GreaterThanOrEqualFilter gf) {
                            return attrPath.goe(value);
                        }
                        if (filter instanceof LessThanFilter lf) {
                            return attrPath.lt(value);
                        }
                        if (filter instanceof LessThanOrEqualFilter lf) {
                            return attrPath.loe(value);
                        }
                    }
                    if (rawPath instanceof DateTimePath<?> attrPath) {
                        var sqlV = sqlValue;
                        var p = ((DateTimePath) attrPath);
                        if (filter instanceof GreaterThanFilter gf) {
                            if (sqlV instanceof Date d) { return p.gt(d); }
                            if (sqlV instanceof Time t) { return p.gt(t); }
                            if (sqlV instanceof Timestamp ts) { return p.gt(ts); }
                            if (sqlV instanceof ZonedDateTime zdt) { return p.gt(zdt.toInstant()); }
                            if (sqlV instanceof Instant i) { return p.gt(i); }
                            return p.gt((java.lang.Comparable) sqlV);
                        }
                        if (filter instanceof GreaterThanOrEqualFilter gf) {
                            if (sqlV instanceof Date d) { return p.goe(d); }
                            if (sqlV instanceof Time t) { return p.goe(t); }
                            if (sqlV instanceof Timestamp ts) { return p.goe(ts); }
                            if (sqlV instanceof ZonedDateTime zdt) { return p.goe(zdt.toInstant()); }
                            if (sqlV instanceof Instant i) { return p.goe(i); }
                            return p.goe((java.lang.Comparable) sqlV);
                        }
                        if (filter instanceof LessThanFilter lf) {
                            if (sqlV instanceof Date d) { return p.lt(d); }
                            if (sqlV instanceof Time t) { return p.lt(t); }
                            if (sqlV instanceof Timestamp ts) { return p.lt(ts); }
                            if (sqlV instanceof ZonedDateTime zdt) { return p.lt(zdt.toInstant()); }
                            if (sqlV instanceof Instant i) { return p.lt(i); }
                            return p.lt((java.lang.Comparable) sqlV);
                        }
                        if (filter instanceof LessThanOrEqualFilter lf) {
                            if (sqlV instanceof Date d) { return p.loe(d); }
                            if (sqlV instanceof Time t) { return p.loe(t); }
                            if (sqlV instanceof Timestamp ts) { return p.loe(ts); }
                            if (sqlV instanceof ZonedDateTime zdt) { return p.loe(zdt.toInstant()); }
                            if (sqlV instanceof Instant i) { return p.loe(i); }
                            return p.loe((java.lang.Comparable) sqlV);
                        }
                    }
                    // FIXME: Maybe throw exception that filter is not supported?
                    throw SqlFilterTranslator.unsupportedFilterException(filter);
                }
            };
        }

        @Override
        public Collection<Path<?>> selectPaths(Path<?> table) {
            return List.of(dslPath(table));
        }

        public Object toSqlValue(Object connIdValue) {
            return valueMapping().toWireValue(connIdValue);
        }

    }

    // ── Composite columns (e.g. for composite primary key) ─────────────────

    record MultiColumn(
            SingleColumn mainColumn,
            List<SingleColumn> additionalColumns,
            String delimiter
    ) implements SqlAttributeMapping {

        @Override public DefinitionValue<String> column() { return mainColumn.column(); }

        @Override public Object attributeFromObject(SqlTuple row) { return valuesFromObject(row); }

        @Override public Class<?> connIdType() { return String.class; }

        @Override public Object singleValueFromAttribute(Object value) { return value; }

        @Override
        public List<Object> valuesFromObject(SqlTuple object) {
            var builder = new StringBuilder().append(mainColumn.singleValueFromObject(object));
            for (var additionalColumn : additionalColumns) {
                builder.append(delimiter).append(additionalColumn.singleValueFromObject(object));
            }
            return List.of(builder.toString());
        }

        @Override
        public List<Object> valuesFromAttribute(Object value) {
            if (value == null) return Collections.emptyList();
            if (value instanceof String s) {
                var parts = s.split(Pattern.quote(delimiter), additionalColumns.size() + 1);
                var result = new ArrayList<Object>();
                for (int i = 0; i < parts.length; i++) {
                    var part = parts[i].trim();
                    try {
                        var cm = (i == 0) ? mainColumn.valueMapping() : additionalColumns.get(i - 1).valueMapping();
                        if (cm != null) { result.add(cm.toConnIdValue(part)); continue; }
                    } catch (Exception ignored) { }
                    result.add(part);
                }
                return result;
            }
            if (value instanceof Collection<?> c) { return new ArrayList<>(c); }
            return List.of(value);
        }

        @Override public FilterSupport sqlFilter() {
            var self = this;
            return new FilterSupport() {
                @Override
                public BooleanExpression predicateFor(RelationalPathBase<?> tp, AttributeFilter filter) {
                    if (filter instanceof EqualsFilter) { return predicateForEquals(tp, filter); }
                    throw SqlFilterTranslator.unsupportedFilterException(filter);
                }
                @Override
                public BooleanExpression predicateFor(RelationalPathBase<?> tp, SingleValueAttributeFilter filter) { return Expressions.FALSE; }

                // --- helper methods ---

                @SuppressWarnings("unchecked")
                private BooleanExpression predicateForEquals(RelationalPathBase<?> tp, AttributeFilter filter) {
                    var connIdValue = filter.getAttribute().getValue();
                    // null/empty → all columns must be NULL
                    if (connIdValue.isEmpty()) {
                        var r = (BooleanExpression) self.mainColumn.dslPath(tp);
                        for (SingleColumn ac : self.additionalColumns) { r = r.and((BooleanExpression) ac.dslPath(tp)); }
                        return r.isNull();
                    }
                    // Split composite UID by delimiter.
                    var uidValue = connIdValue.getFirst().toString();
                    var parts = uidValue.split(delimiter, self.additionalColumns.size() + 1);
                    if (parts.length != self.additionalColumns.size() + 1) {
                        throw new IllegalArgumentException(
                                "UID has wrong number of parts: expected " + (self.additionalColumns.size() + 1) +
                                        ", got " + parts.length);
                    }
                    BooleanExpression result = null;
                    for (int i = 0; i < parts.length; i++) {
                        SingleColumn col = (i == 0) ? self.mainColumn : self.additionalColumns.get(i - 1);
                        var part = parts[i];
                        var sqlValue = toSqlValue(part, col.sqlMapping());
                        var eq = createEqPredicate(col.dslPath(tp), sqlValue);
                        result = (result == null) ? eq : result.and(eq);
                    }
                    return result;
                }

                private BooleanExpression createEqPredicate(Path<?> qPath, Object value) {
                    if (qPath instanceof SimpleExpression expression) { return expression.eq(value); }
                    throw new IllegalArgumentException("Unsupported type for EQ: " + qPath.getClass().getSimpleName());
                }

                private Object toSqlValue(String rawValue, SqlValueMapping sqlMapping) {
                    if (sqlMapping instanceof SqlSchemaValueMapping sm) {
                        var j = sm.jdbcType();
                        if (j == JDBCType.INTEGER || j == JDBCType.BIGINT || j == JDBCType.SMALLINT || j == JDBCType.TINYINT) { return Long.parseLong(rawValue); }
                        if (j == JDBCType.DECIMAL || j == JDBCType.NUMERIC) { return new BigDecimal(rawValue); }
                        if (j == JDBCType.BIT || j == JDBCType.BOOLEAN) { return Boolean.parseBoolean(rawValue); }
                        if (j == JDBCType.DATE || j == JDBCType.TIME || j == JDBCType.TIMESTAMP || j == JDBCType.TIMESTAMP_WITH_TIMEZONE) { return sm.toWireValue(rawValue); }
                    }
                    return rawValue;
                }
            };
        }

        @Override public Collection<Path<?>> selectPaths(Path<?> table) {
            var paths = new ArrayList<Path<?>>();
            paths.add(mainColumn.dslPath(table));
            for (var ac : additionalColumns) { paths.add(ac.dslPath(table)); }
            return paths;
        }

        public Object toSqlValue(Object value) { return mainColumn.toSqlValue(value); }
    }
}
