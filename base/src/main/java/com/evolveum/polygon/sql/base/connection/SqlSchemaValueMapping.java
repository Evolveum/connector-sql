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
 * <p>Analogous to {@code JsonSchemaValueMapping} in the conndev-base framework,
 * but tailored for SQL column types (VARCHAR, INT, TIMESTAMP, etc.).</p>
 */
public enum SqlSchemaValueMapping implements SqlValueMapping.SingleColumn {
    VARCHAR(JDBCType.VARCHAR, String.class, String.class, Expressions::stringPath),
    INTEGER(JDBCType.INTEGER, Integer.class, Integer.class, QueryDslUtils.numberPath(Integer.class)),
    SMALLINT(JDBCType.SMALLINT, Integer.class, Short.class, QueryDslUtils.numberPath(Short.class)) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value instanceof Number n) {
                return n.intValue();
            }
            return super.toConnIdValue(value);
        }
    },
    NUMERIC(JDBCType.NUMERIC, BigDecimal.class, BigDecimal.class, QueryDslUtils.numberPath(BigDecimal.class)),
    TINYINT(JDBCType.TINYINT, Integer.class, Integer.class, QueryDslUtils.numberPath(Integer.class)),
    BIGINT(JDBCType.BIGINT, BigInteger.class, BigInteger.class, QueryDslUtils.numberPath(BigInteger.class)) ,
    DECIMAL(JDBCType.DECIMAL, BigDecimal.class, BigDecimal.class, QueryDslUtils.numberPath(BigDecimal.class)),
    FLOAT(JDBCType.FLOAT, Double.class, Float.class, QueryDslUtils.numberPath(Float.class)),
    DOUBLE(JDBCType.DOUBLE, Double.class, Double.class, QueryDslUtils.numberPath(Double.class)),
    BOOLEAN(JDBCType.BOOLEAN, Boolean.class, Boolean.class, Expressions::booleanPath),
    BIT(JDBCType.BIT, Boolean.class, Integer.class, QueryDslUtils.numberPath(Integer.class)) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Integer i) {
                return i != 0;
            }
            if (value instanceof Boolean b) {
                return b;
            }
            return super.toConnIdValue(value);
        }
    },
    DATE(JDBCType.DATE, LocalDate.class, Date.class, QueryDslUtils.dateTimePath(Date.class)) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value instanceof Date d) {
                return d.toLocalDate();
            }
            return super.toConnIdValue(value);
        }

        @Override
        public Object toWireValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Date) {
                return value;
            }
            if (value instanceof String s) {
                try {
                    return Date.valueOf(s);
                } catch (IllegalArgumentException e) {
                    try {
                        return Date.valueOf(s.substring(0, 10));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Cannot parse string '" + s + "' to java.sql.Date", ex);
                    }
                }
            }
            if (value instanceof LocalDate ld) {
                return Date.valueOf(ld);
            }
            if (value instanceof TemporalAccessor t) {
                var iso = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.from(t));
                return Date.valueOf(iso);
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to java.sql.Date");
        }
    },
    TIME(JDBCType.TIME, LocalTime.class, Time.class, QueryDslUtils.timePath(Time.class)) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value instanceof Time t) {
                return t.toLocalTime();
            }
            return super.toConnIdValue(value);
        }

        @Override
        public Object toWireValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Time) {
                return value;
            }
            if (value instanceof String s) {
                try {
                    return Time.valueOf(s);
                } catch (IllegalArgumentException e) {
                    try {
                        return Time.valueOf(s.substring(s.indexOf(' ') + 1));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Cannot parse string '" + s + "' to java.sql.Time", ex);
                    }
                }
            }
            if (value instanceof LocalTime lt) {
                return Time.valueOf(lt);
            }
            if (value instanceof TemporalAccessor t) {
                var iso = DateTimeFormatter.ISO_LOCAL_TIME.format(LocalTime.from(t));
                return Time.valueOf(iso);
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to java.sql.Time");
        }
    },
    TIMESTAMP(JDBCType.TIMESTAMP, ZonedDateTime.class, Timestamp.class, QueryDslUtils.dateTimePath(Timestamp.class)) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value instanceof Timestamp ts) {
                return ts.toInstant().atZone(ZoneId.systemDefault());
            }
            return super.toConnIdValue(value);
        }

        @Override
        public Object toWireValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Timestamp) {
                return value;
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp;
            }
            if (value instanceof String s) {
                try {
                    return Timestamp.valueOf(s);
                } catch (IllegalArgumentException e) {
                    try {
                        return Timestamp.valueOf(s.replace('T', ' '));
                    } catch (IllegalArgumentException ex) {
                        try {
                            return Timestamp.valueOf(s.substring(0, 10) + " 00:00:00");
                        } catch (IllegalArgumentException ex2) {
                            throw new IllegalArgumentException("Cannot parse string '" + s + "' to java.sql.Timestamp", ex2);
                        }
                    }
                }
            }
            if (value instanceof Date d) {
                return new Timestamp(d.getTime());
            }
            if (value instanceof Time t) {
                return new Timestamp(t.getTime());
            }
            if (value instanceof ZonedDateTime zdt) {
                return Timestamp.from(zdt.toInstant());
            }
            if (value instanceof Instant i) {
                return Timestamp.from(i);
            }
            if (value instanceof LocalDate ld) {
                return Timestamp.valueOf(ld.atStartOfDay());
            }
            if (value instanceof TemporalAccessor t) {
                var iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.from(t));
                return Timestamp.valueOf(iso);
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to java.sql.Timestamp");
        }
    },
    BLOB(JDBCType.BLOB, byte[].class, byte[].class, QueryDslUtils::byteArrayPath),
    CLOB(JDBCType.CLOB, String.class, String.class, Expressions::stringPath),
    TIMESTAMP_WITH_TIMEZONE(JDBCType.TIMESTAMP_WITH_TIMEZONE, ZonedDateTime.class, ZonedDateTime.class, QueryDslUtils.dateTimePath(ZonedDateTime.class)) {
        @Override
        public Object toWireValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Timestamp) {
                return value;
            }
            if (value instanceof String s) {
                try {
                    return Timestamp.valueOf(s);
                } catch (IllegalArgumentException e) {
                    try {
                        return Timestamp.valueOf(s.replace('T', ' '));
                    } catch (IllegalArgumentException ex) {
                        try {
                            return Timestamp.valueOf(s.substring(0, 10) + " 00:00:00");
                        } catch (IllegalArgumentException ex2) {
                            throw new IllegalArgumentException("Cannot parse string '" + s + "' to java.sql.Timestamp", ex2);
                        }
                    }
                }
            }
            if (value instanceof ZonedDateTime zdt) {
                return Timestamp.from(zdt.toInstant());
            }
            if (value instanceof Instant i) {
                return Timestamp.from(i);
            }
            if (value instanceof Date d) {
                return new Timestamp(d.getTime());
            }
            if (value instanceof Time t) {
                return new Timestamp(t.getTime());
            }
            if (value instanceof TemporalAccessor t) {
                var iso = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.from(t));
                return Timestamp.valueOf(iso);
            }
            throw new IllegalArgumentException("Cannot convert " + value.getClass().getSimpleName() + " to JDBC timestamp value");
        }
    };

    private final JDBCType jdbcType;
    private final Class<?> connIdClass;
    private final Class<?> wireType;
    private final BiFunction<Path<?>, String, ? extends Path<?>> pathSupplier;

    SqlSchemaValueMapping(JDBCType jdbcType, Class<?> connIdClass, Class<?> wireType, BiFunction<Path<?>, String, ? extends Path<?>> pathSupplier) {
        this.jdbcType = jdbcType;
        this.connIdClass = connIdClass;
        this.wireType = wireType;
        this.pathSupplier = pathSupplier;
    }

    @Override
    public Class<?> connIdType() {
        return connIdClass;
    }

    @Override
    public Class<?> primaryWireType() {
        return wireType;
    }

    @Override
    public Set<Class<?>> supportedWireTypes() {
        return Set.of(wireType);
    }

    /**
     * Converts a wire value (raw JDBC value) to a ConnId value.
     */
    @Override
    public Object toConnIdValue(Object value) {
        if (value == null) {
            return null;
        }
        if (connIdClass.isAssignableFrom(value.getClass())) {
            return value;
        }
        throw new IllegalArgumentException("Can not convert SQL value" +  value + " to ConnId " + connIdClass.getSimpleName());
    }

    /**
     * Converts a ConnId value to a wire value for writing back to the database.
     */
    @Override
    public Object toWireValue(Object value) {
        if (value == null) {
            return null;
        }
        return value;
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
        if (upper.contains("DECIMAL") || upper.contains("NUMBER") || upper.contains("NUMERIC")) {
            return DECIMAL;
        }
        if (upper.contains("FLOAT") || upper.contains("REAL")) {
            return FLOAT;
        }
        if (upper.contains("DOUBLE")) {
            return DOUBLE;
        }
        if (upper.contains("BOOLEAN") || upper.contains("BOOL") || upper.contains("BIT")) {
            return BIT;
        }
        if (upper.contains("TIMESTAMP")) {
            return TIMESTAMP;
        }
        if (upper.contains("DATE")) {
            return DATE;
        }
        if (upper.contains("TIME")) {
            return TIME;
        }
        if (upper.contains("BLOB")) {
            return BLOB;
        }

        // Default fallback for text
        return VARCHAR;
    }

    /**
     * Looks up the SqlSchemaValueMapping by JDBC SQL type constant.
     */
    public static SqlSchemaValueMapping fromJdbcType(int sqlType) {
        switch (sqlType) {
            case Types.VARCHAR:
            case Types.CHAR:
            case Types.LONGVARCHAR:
                return VARCHAR;
            case Types.INTEGER:
                return INTEGER;
            case Types.BIGINT:
                return BIGINT;
            case Types.SMALLINT:
                return SMALLINT;
            case Types.TINYINT:
                return TINYINT;
            case Types.DECIMAL:
            case Types.NUMERIC:
                return DECIMAL;
            case Types.FLOAT:
            case Types.REAL:
                return FLOAT;
            case Types.DOUBLE:
                return DOUBLE;
            case Types.BOOLEAN:
            case Types.BIT:
                return BIT;
            case Types.DATE:
                return DATE;
            case Types.TIME:
                return TIME;
            case Types.TIMESTAMP:
                return TIMESTAMP;
            case Types.BLOB:
                return BLOB;
            default:
                return null;
        }
    }

    @Override
    public JDBCType jdbcType() {
        return jdbcType;
    }

    @Override
    public Path<?> pathFor(Path<?> parent, String column) {
        return pathSupplier.apply(parent, column);
    }
}