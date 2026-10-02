/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.sql.base.build.api.SqlTypeSpecification;
import com.querydsl.core.types.Path;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.JDBCType;
import java.sql.Types;
import java.util.Map;

/**
 * Enum of SQL schema value mappings between SQL column types and ConnId wire types.
 * Each constant defines a ConnId Java class (the wire type for attribute values)
 * and provides bidirectional conversion between raw JDBC values and ConnId values.
 *
 * <p>Delegates QueryDSL-Java-type ↔ ConnId-type conversions to
 * {@link QueryDslTypeMapping}, and adds SQL-specific concerns:
 * JDBC type codes, exact type-name matching with database-specific aliases,
 * QueryDSL path creation, and extended string-parsing in {@code toWireValue()} for API input.</p>
 *
 * <p>Analogous to {@code JsonSchemaValueMapping} in the conndev-base framework,
 * but tailored for SQL column types (VARCHAR, INT, TIMESTAMP, etc.).</p>
 */
public enum SqlSchemaValueMapping implements SqlValueMapping.SingleColumn {
    VARCHAR(JDBCType.VARCHAR, QueryDslTypeMapping.STRING),
    INTEGER(JDBCType.INTEGER, QueryDslTypeMapping.INTEGER),
    SMALLINT(JDBCType.SMALLINT, QueryDslTypeMapping.SMALL_INT),
    NUMERIC(JDBCType.NUMERIC, QueryDslTypeMapping.DECIMAL),
    TINYINT(JDBCType.TINYINT, QueryDslTypeMapping.INTEGER),
    BIGINT(JDBCType.BIGINT, QueryDslTypeMapping.BIG_INT),
    DECIMAL(JDBCType.DECIMAL, QueryDslTypeMapping.DECIMAL),
    FLOAT(JDBCType.FLOAT,QueryDslTypeMapping.FLOAT_NUM),
    DOUBLE(JDBCType.DOUBLE,QueryDslTypeMapping.DOUBLE),
    BOOLEAN(JDBCType.BOOLEAN, QueryDslTypeMapping.BOOLEAN),
    BIT(JDBCType.BIT, QueryDslTypeMapping.BOOLEAN),
    DATE(JDBCType.DATE, QueryDslTypeMapping.SQL_DATE),
    TIME(JDBCType.TIME, QueryDslTypeMapping.SQL_TIME),
    TIMESTAMP(JDBCType.TIMESTAMP, QueryDslTypeMapping.SQL_TIMESTAMP),
    BLOB(JDBCType.BLOB, QueryDslTypeMapping.BYTE_ARRAY),
    CLOB(JDBCType.CLOB, QueryDslTypeMapping.STRING),
    TIMESTAMP_WITH_TIMEZONE(JDBCType.TIMESTAMP_WITH_TIMEZONE,QueryDslTypeMapping.SQL_TIMESTAMP_TZ);

    private final JDBCType jdbcType;
    private final QueryDslTypeMapping<?,?> mapping;

    SqlSchemaValueMapping(JDBCType jdbcType, QueryDslTypeMapping<?,?> mapping) {
        this.jdbcType = jdbcType;
        this.mapping = mapping;
    }

    /**
     * The {@link QueryDslTypeMapping} that handles QueryDSL Java type → ConnId type conversions.
     * Each enum constant overrides this to return its corresponding mapping.
     */
    public final QueryDslTypeMapping<?,?> qdslTypeMapping() { return mapping; }

    @Override
    public Class<?> connIdType() {
        return mapping.connIdType();
    }

    @Override
    public Class<?> primaryWireType() {
        return mapping.primaryWireType();
    }

    @Override
    public Object toConnIdValue(Object value) {
        return mapping.toConnIdValue(value);
    }

    @Override
    public Object toWireValue(Object value) {
        return mapping.toWireValue(value);
    }

    /**
     * Coerces an arbitrary value (e.g. a ConnId- or script-supplied value) to this mapping's
     * wire type, for binding into SQL statements. Values already at the wire type pass
     * through unchanged; strings are parsed and numbers converted without truncation or
     * silent overflow of a narrower integer type.
     */
    public Object coerceToWireValue(Object value) {
        if (value == null) {
            return null;
        }
        var wireType = primaryWireType();
        if (wireType.isInstance(value)) {
            return value;
        }
        if (value instanceof String stringValue) {
            return parse(stringValue, wireType);
        }
        if (value instanceof Number number
                && Number.class.isAssignableFrom(wireType)) {
            return convertNumber(number, wireType);
        }
        return toWireValue(value);
    }

    private static Object convertNumber(Number value, Class<?> targetType) {
        var decimal = new BigDecimal(value.toString());
        // Foreign keys may use a different numeric JDBC type than their referenced column.
        // Do not silently truncate a fractional value or overflow a narrower integer type.
        if (targetType == BigInteger.class) {
            return decimal.toBigIntegerExact();
        }
        if (targetType == Integer.class) {
            return decimal.intValueExact();
        }
        if (targetType == Long.class) {
            return decimal.longValueExact();
        }
        if (targetType == Short.class) {
            return decimal.shortValueExact();
        }
        if (targetType == Byte.class) {
            return decimal.byteValueExact();
        }
        return parse(decimal.toString(), targetType);
    }

    private static Object parse(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        }
        if (targetType == BigInteger.class) {
            return new BigInteger(value);
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (targetType == Integer.class) {
            return Integer.valueOf(value);
        }
        if (targetType == Long.class) {
            return Long.valueOf(value);
        }
        if (targetType == Short.class) {
            return Short.valueOf(value);
        }
        if (targetType == Byte.class) {
            return Byte.valueOf(value);
        }
        if (targetType == Double.class) {
            return Double.valueOf(value);
        }
        if (targetType == Float.class) {
            return Float.valueOf(value);
        }
        if (targetType == Boolean.class) {
            return Boolean.valueOf(value);
        }
        return value;
    }

    /**
     * Looks up the SqlSchemaValueMapping by QueryDSL Java type (from {@link QueryDslTypeMapping}).
     * This is the primary way to resolve a mapping when QueryDSL's {@code getJavaType()} already
     * produced the Java class.
     *
     * <p>For ambiguous types (e.g., both TIMESTAMP and TIMESTAMP_WITH_TIMEZONE may map to
     * the same wire type), returns the first match. Use {@link #fromJdbcType(int)} for
     * unambiguous resolution when the JDBC type code is available.</p>
     */
    public static SqlSchemaValueMapping fromQdslJavaType(Class<?> javaType) {
        if (javaType == null) {
            return null;
        }
        for (SqlSchemaValueMapping m : values()) {
            if (m.qdslTypeMapping() != null
                    && m.primaryWireType().isAssignableFrom(javaType)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public Path<?> pathFor(Path<?> parent, String column) {
        return mapping.pathFor(parent, column);
    }

    /**
     * Database-specific SQL type name aliases (matched exactly, case-insensitively) — the
     * canonical {@link JDBCType} names of the enum constants are matched directly, this table
     * covers the variants used by specific databases. Size/precision suffixes are stripped by
     * the callers before lookup.
     */
    private static final Map<String, SqlSchemaValueMapping> TYPE_ALIASES = Map.ofEntries(
            // character types
            Map.entry("CHAR", VARCHAR),
            Map.entry("CHARACTER", VARCHAR),
            Map.entry("CHARACTER VARYING", VARCHAR),
            Map.entry("CHAR VARYING", VARCHAR),
            Map.entry("VARCHAR2", VARCHAR),
            Map.entry("NCHAR", VARCHAR),
            Map.entry("NCHAR VARYING", VARCHAR),
            Map.entry("NVARCHAR", VARCHAR),
            Map.entry("NVARCHAR2", VARCHAR),
            Map.entry("NATIONAL CHARACTER", VARCHAR),
            Map.entry("NATIONAL CHARACTER VARYING", VARCHAR),
            Map.entry("LONG VARCHAR", VARCHAR),
            Map.entry("LONGNVARCHAR", VARCHAR),
            Map.entry("UUID", VARCHAR),
            // integer types
            Map.entry("INT", INTEGER),
            Map.entry("INT2", SMALLINT),
            Map.entry("INT4", INTEGER),
            Map.entry("INT8", BIGINT),
            Map.entry("MEDIUMINT", INTEGER),
            Map.entry("SERIAL", INTEGER),
            Map.entry("SERIAL2", SMALLINT),
            Map.entry("SERIAL4", INTEGER),
            Map.entry("SERIAL8", BIGINT),
            Map.entry("SMALLSERIAL", SMALLINT),
            Map.entry("BIGSERIAL", BIGINT),
            Map.entry("IDENTITY", INTEGER),
            // exact numeric types
            Map.entry("DEC", DECIMAL),
            Map.entry("NUMBER", NUMERIC),
            Map.entry("MONEY", NUMERIC),
            Map.entry("SMALLMONEY", NUMERIC),
            // approximate numeric types
            Map.entry("REAL", FLOAT),
            Map.entry("FLOAT4", FLOAT),
            Map.entry("FLOAT8", DOUBLE),
            Map.entry("DOUBLE PRECISION", DOUBLE),
            // boolean types
            Map.entry("BOOL", BOOLEAN),
            // bit types
            Map.entry("VARBIT", BIT),
            // date and time types
            Map.entry("DATETIME", TIMESTAMP),
            Map.entry("TIMESTAMP WITH TIME ZONE", TIMESTAMP_WITH_TIMEZONE),
            Map.entry("TIMESTAMP WITHOUT TIME ZONE", TIMESTAMP),
            Map.entry("TIMESTAMPTZ", TIMESTAMP_WITH_TIMEZONE),
            Map.entry("TIME WITH TIME ZONE", TIME),
            // binary types
            Map.entry("BINARY", BLOB),
            Map.entry("BINARY VARYING", BLOB),
            Map.entry("VARBINARY", BLOB),
            Map.entry("LONGVARBINARY", BLOB),
            Map.entry("BYTEA", BLOB),
            Map.entry("RAW", BLOB),
            Map.entry("LONG RAW", BLOB),
            Map.entry("TINYBLOB", BLOB),
            Map.entry("MEDIUMBLOB", BLOB),
            Map.entry("LONGBLOB", BLOB),
            // large character types
            Map.entry("NCLOB", CLOB),
            Map.entry("NTEXT", CLOB),
            Map.entry("TEXT", CLOB),
            Map.entry("TINYTEXT", CLOB),
            Map.entry("MEDIUMTEXT", CLOB),
            Map.entry("LONGTEXT", CLOB),
            Map.entry("XML", CLOB),
            Map.entry("JSON", CLOB)
    );

    /**
     * Looks up the SqlSchemaValueMapping by SQL type name. Matching is exact and
     * case-insensitive: the canonical {@link JDBCType} name of a mapping first, then the
     * database-specific aliases in {@link #TYPE_ALIASES}. Returns {@code null} for an
     * unrecognized name — callers are expected to reject it.
     */
    public static SqlSchemaValueMapping fromTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        var name = typeName.toUpperCase().trim();
        for (SqlSchemaValueMapping m : values()) {
            if (m.jdbcType.toString().equals(name)) {
                return m;
            }
        }
        return TYPE_ALIASES.get(name);
    }

    /**
     * Looks up the SqlSchemaValueMapping by JDBC SQL type constant.
     */
    public static SqlSchemaValueMapping fromJdbcType(int sqlType) {
        return switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR -> VARCHAR;
            case Types.INTEGER -> INTEGER;
            case Types.BIGINT -> BIGINT;
            case Types.SMALLINT -> SMALLINT;
            case Types.TINYINT -> TINYINT;
            case Types.DECIMAL -> DECIMAL;
            case Types.NUMERIC -> NUMERIC;
            case Types.FLOAT, Types.REAL -> FLOAT;
            case Types.DOUBLE -> DOUBLE;
            case Types.BOOLEAN -> BOOLEAN;
            case Types.BIT -> BIT;
            case Types.DATE -> DATE;
            case Types.TIME -> TIME;
            case Types.TIMESTAMP -> TIMESTAMP;
            case Types.TIMESTAMP_WITH_TIMEZONE -> TIMESTAMP_WITH_TIMEZONE;
            case Types.TIME_WITH_TIMEZONE -> TIMESTAMP;
            case Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY -> BLOB;
            case Types.CLOB, Types.NCLOB -> CLOB;
            default -> null;
        };
    }

    public SqlTypeSpecification asTypeSpecification() {
        return new  SqlTypeSpecification.BuiltIn(this);
    }
}
