/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import java.math.BigDecimal;
import java.sql.JDBCType;
import java.sql.Timestamp;
import java.time.*;
import java.util.Set;

/**
 * Enum of SQL schema value mappings between SQL column types and ConnId wire types.
 * Each constant defines a ConnId Java class (the wire type for attribute values)
 * and provides bidirectional conversion between raw JDBC values and ConnId values.
 *
 * <p>Analogous to {@code JsonSchemaValueMapping} in the conndev-base framework,
 * but tailored for SQL column types (VARCHAR, INT, TIMESTAMP, etc.).</p>
 */
public enum SqlSchemaValueMapping implements SqlValueMapping {
    VARCHAR(JDBCType.VARCHAR, String.class, String.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String s) {
                return s;
            }
            if (value instanceof byte[] bytes) {
                return new String(bytes);
            }
            if (value instanceof java.sql.Clob clob) {
                try (var reader = clob.getCharacterStream()) {
                    var sb = new StringBuilder();
                    int ch;
                    while ((ch = reader.read()) != -1) {
                        sb.append((char) ch);
                    }
                    return sb.toString();
                } catch (Exception e) {
                    return value.toString();
                }
            }
            return value.toString();
        }
    },
    INTEGER(JDBCType.INTEGER, Integer.class, Integer.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.intValue();
            }
            return super.toConnIdValue(value);
        }
    },
    SMALLINT(JDBCType.SMALLINT, Integer.class, Short.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.shortValue();
            }
            return super.toConnIdValue(value);
        }
    },
    TINYINT(JDBCType.TINYINT, Integer.class, Byte.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.byteValue();
            }
            return super.toConnIdValue(value);
        }
    },
    BIGINT(JDBCType.BIGINT, Long.class, Long.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.longValue();
            }
            return super.toConnIdValue(value);
        }
    },
    DECIMAL(JDBCType.DECIMAL, Double.class, Number.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                if (n instanceof BigDecimal bd) {
                    return bd.doubleValue();
                }
                return n.doubleValue();
            }
            try {
                return Double.parseDouble(value.toString());
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public Object toWireValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof String s) {
                try {
                    return new BigDecimal(s);
                } catch (Exception e) {
                    return s;
                }
            }
            if (value instanceof Number n) {
                return new BigDecimal(n.doubleValue());
            }
            return super.toWireValue(value);
        }
    },
    FLOAT(JDBCType.FLOAT, Double.class, Float.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.floatValue();
            }
            return super.toConnIdValue(value);
        }
    },
    DOUBLE(JDBCType.DOUBLE, Double.class, Double.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            return super.toConnIdValue(value);
        }
    },
    BOOLEAN(JDBCType.BOOLEAN, Boolean.class, Boolean.class),
    BIT(JDBCType.BIT, Boolean.class, Integer.class) {
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
    DATE(JDBCType.DATE, LocalDate.class, java.sql.Date.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof LocalDate d) {
                return d;
            }
            if (value instanceof java.sql.Date d) {
                return d.toLocalDate();
            }
            if (value instanceof java.util.Date d) {
                return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            }
            if (value instanceof Timestamp ts) {
                return ts.toLocalDateTime().toLocalDate();
            }
            return value.toString();
        }
    },
    TIME(JDBCType.TIME, LocalTime.class, java.sql.Time.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof LocalTime t) {
                return t;
            }
            if (value instanceof java.sql.Time t) {
                return t.toLocalTime();
            }
            if (value instanceof java.util.Date t) {
                return t.toInstant().atZone(ZoneId.systemDefault()).toLocalTime();
            }
            if (value instanceof Timestamp ts) {
                return ts.toLocalDateTime().toLocalTime();
            }
            return value.toString();
        }
    },
    TIMESTAMP(JDBCType.TIMESTAMP, ZonedDateTime.class, Timestamp.class) {
        @Override
        public Object toConnIdValue(Object value) {
            if (value == null) {
                return null;
            }
            if (value instanceof ZonedDateTime zdt) {
                return zdt;
            }
            if (value instanceof Timestamp ts) {
                return ts.toInstant().atZone(ZoneId.systemDefault());
            }
            if (value instanceof java.time.Instant i) {
                return i.atZone(ZoneId.systemDefault());
            }
            if (value instanceof OffsetDateTime odt) {
                return odt.toInstant().atZone(ZoneId.systemDefault());
            }
            if (value instanceof LocalDateTime ldt) {
                return ldt.atZone(ZoneId.systemDefault());
            }
            if (value instanceof LocalDate ld) {
                return ld.atStartOfDay(ZoneId.systemDefault());
            }
            if (value instanceof String s) {
                try {
                    return ZonedDateTime.parse(s);
                } catch (Exception e) {
                    try {
                        return Timestamp.valueOf(s).toInstant().atZone(ZoneId.systemDefault());
                    } catch (Exception ex) {
                        return s;
                    }
                }
            }
            return value.toString();
        }
    },
    BLOB(JDBCType.BLOB, byte[].class, byte[].class),
    CLOB(JDBCType.CLOB, String.class, String.class);

    private final JDBCType jdbcType;
    private final Class<?> connIdClass;
    private final Class<?> wireType;

    SqlSchemaValueMapping(JDBCType jdbcType, Class<?> connIdClass, Class<?> wireType) {
        this.jdbcType = jdbcType;
        this.connIdClass = connIdClass;
        this.wireType = wireType;
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
        return value.toString();
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
            case java.sql.Types.VARCHAR:
            case java.sql.Types.CHAR:
            case java.sql.Types.LONGVARCHAR:
                return VARCHAR;
            case java.sql.Types.INTEGER:
                return INTEGER;
            case java.sql.Types.BIGINT:
                return BIGINT;
            case java.sql.Types.SMALLINT:
                return SMALLINT;
            case java.sql.Types.TINYINT:
                return TINYINT;
            case java.sql.Types.DECIMAL:
            case java.sql.Types.NUMERIC:
                return DECIMAL;
            case java.sql.Types.FLOAT:
            case java.sql.Types.REAL:
                return FLOAT;
            case java.sql.Types.DOUBLE:
                return DOUBLE;
            case java.sql.Types.BOOLEAN:
            case java.sql.Types.BIT:
                return BIT;
            case java.sql.Types.DATE:
                return DATE;
            case java.sql.Types.TIME:
                return TIME;
            case java.sql.Types.TIMESTAMP:
                return TIMESTAMP;
            case java.sql.Types.BLOB:
                return BLOB;
            default:
                return null;
        }
    }

    @Override
    public JDBCType jdbcType() {
        return jdbcType;
    }
}