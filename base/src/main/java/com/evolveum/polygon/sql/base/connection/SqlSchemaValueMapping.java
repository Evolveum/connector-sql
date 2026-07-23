/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.Expressions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Enum of SQL schema value mappings between SQL column types and ConnId wire types.
 * Each constant defines a ConnId Java class (the wire type for attribute values)
 * and provides bidirectional conversion between raw JDBC values and ConnId values.
 *
 * <p>Delegates QueryDSL-Java-type ↔ ConnId-type conversions to
 * {@link QueryDslTypeMapping}, and adds SQL-specific concerns:
 * JDBC type codes, type name fuzzy matching, QueryDSL path creation,
 * and extended string-parsing in {@code toWireValue()} for API input.</p>
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
     * Looks up the SqlSchemaValueMapping by SQL type name.
     * Matches against known SQL type names (VARCHAR, INT, BIGINT, etc.).
     */
    public static SqlSchemaValueMapping fromTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }
        var upper = typeName.toUpperCase().trim();

        // Direct match
        for (SqlSchemaValueMapping m : values()) {
            if (m.jdbcType.toString().equals(upper)) {
                return m;
            }
        }

        // Pattern match for database-specific type names
        if (upper.contains("VAR") && upper.contains("CHAR")) {
            return VARCHAR;
        }
        if (upper.contains("NVARCHAR") || upper.contains("NCHAR")) {
            return VARCHAR;
        }
        if (upper.contains("CHAR") && !upper.contains("VAR")) {
            return VARCHAR;
        }
        if (upper.contains("INT") && !upper.contains("TIMESTAMP")) {
            if (upper.contains("BIG")) {
                return BIGINT;
            }
            if (upper.contains("SMALL")) {
                return SMALLINT;
            }
            if (upper.contains("TINY")) {
                return TINYINT;
            }
            return INTEGER;
        }
        if (upper.contains("DECIMAL")) {
            return DECIMAL;
        }
        if (upper.contains("NUMBER") || upper.contains("NUMERIC")) {
            return NUMERIC;
        }
        if (upper.contains("FLOAT") || upper.contains("REAL")) {
            return FLOAT;
        }
        if (upper.contains("DOUBLE")) {
            return DOUBLE;
        }
        if (upper.contains("BOOLEAN") || upper.contains("BOOL")) {
            return BOOLEAN;
        }
        if (upper.contains("BIT")) {
            return BIT;
        }
        if (upper.contains("TIMESTAMP") || upper.contains("DATETIME")) {
            if (upper.contains("TIME ZONE") || upper.contains("TIMESTAMPTZ")) {
                return TIMESTAMP_WITH_TIMEZONE;
            }
            return TIMESTAMP;
        }
        if (upper.contains("DATE")) {
            return DATE;
        }
        if (upper.contains("TIME")) {
            return TIME;
        }
        if (upper.contains("CLOB") || upper.contains("NCLOB")) {
            return CLOB;
        }
        if (upper.contains("BLOB") || upper.contains("BINARY")) {
            return BLOB;
        }

        // Default fallback for text
        return VARCHAR;
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
}
