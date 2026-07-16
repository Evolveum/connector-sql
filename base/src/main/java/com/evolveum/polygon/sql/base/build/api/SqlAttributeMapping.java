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
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.*;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.filter.*;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    default FilterSupport sqlFilter() {
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
}
