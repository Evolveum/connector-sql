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
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.Expressions;

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
public record SqlAttributeMapping(DefinitionValue<String> column,
                                  SqlValueMapping sqlMapping, ValueMapping<Object, Object> valueMapping) implements AttributeProtocolMapping<Map<String, Object>, Object> {

    /**
     * Extracts the column value from the SQL row.
     */
    @Override
    public Object attributeFromObject(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return row.get(column.value());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SqlAttributeMapping that)) {
            return false;
        }
        return column.equals(that.column);
    }

    @Override
    public int hashCode() {
        return column.hashCode();
    }

    @Override
    public String toString() {
        return "SqlAttributeMapping{column='" + column + "', connIdType=" + connIdType() + "}";
    }

    public Path<?> dslPath(Path<?> parent) {
        if (sqlMapping instanceof SqlValueMapping.SingleColumn single) {
            return single.pathFor(parent, column.value());
        }
        return Expressions.path(Object.class, parent, column.value());
    }

    public Object toSqlValue(Object connIdValue) {
        return valueMapping().toWireValue(connIdValue);
    }
}