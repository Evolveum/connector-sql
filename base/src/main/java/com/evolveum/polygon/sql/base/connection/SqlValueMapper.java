/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapper for converting between SQL types and ConnId types.
 */
public class SqlValueMapper {

    private static final Map<Integer, Class<?>> SQL_TO_CONNID = new HashMap<>();
    private static final Map<Class<?>, Integer> CONNID_TO_SQL = new HashMap<>();

    static {
        SQL_TO_CONNID.put(Types.CHAR, String.class);
        SQL_TO_CONNID.put(Types.VARCHAR, String.class);
        SQL_TO_CONNID.put(Types.LONGVARCHAR, String.class);
        SQL_TO_CONNID.put(Types.NUMERIC, Number.class);
        SQL_TO_CONNID.put(Types.DECIMAL, Number.class);
        SQL_TO_CONNID.put(Types.BIT, Boolean.class);
        SQL_TO_CONNID.put(Types.BOOLEAN, Boolean.class);
        SQL_TO_CONNID.put(Types.TINYINT, Byte.class);
        SQL_TO_CONNID.put(Types.SMALLINT, Short.class);
        SQL_TO_CONNID.put(Types.INTEGER, Integer.class);
        SQL_TO_CONNID.put(Types.BIGINT, Long.class);
        SQL_TO_CONNID.put(Types.REAL, Float.class);
        SQL_TO_CONNID.put(Types.FLOAT, Double.class);
        SQL_TO_CONNID.put(Types.DOUBLE, Double.class);
        SQL_TO_CONNID.put(Types.DATE, java.sql.Date.class);
        SQL_TO_CONNID.put(Types.TIME, java.sql.Time.class);
        SQL_TO_CONNID.put(Types.TIMESTAMP, java.sql.Timestamp.class);
        SQL_TO_CONNID.put(Types.BINARY, byte[].class);
        SQL_TO_CONNID.put(Types.VARBINARY, byte[].class);
        SQL_TO_CONNID.put(Types.LONGVARBINARY, byte[].class);
        SQL_TO_CONNID.put(Types.NULL, Void.class);
        SQL_TO_CONNID.put(Types.OTHER, Object.class);
        SQL_TO_CONNID.put(Types.JAVA_OBJECT, Object.class);
        SQL_TO_CONNID.put(Types.DISTINCT, Object.class);
        SQL_TO_CONNID.put(Types.STRUCT, Object.class);
        SQL_TO_CONNID.put(Types.ARRAY, Object.class);
        SQL_TO_CONNID.put(Types.CLOB, String.class);
        SQL_TO_CONNID.put(Types.BLOB, byte[].class);
        SQL_TO_CONNID.put(Types.NCHAR, String.class);
        SQL_TO_CONNID.put(Types.NVARCHAR, String.class);
        SQL_TO_CONNID.put(Types.LONGNVARCHAR, String.class);
        SQL_TO_CONNID.put(Types.NCLOB, String.class);
        SQL_TO_CONNID.put(Types.SQLXML, String.class);
    }

    public static Class<?> toConnIdType(int sqlType) {
        return SQL_TO_CONNID.getOrDefault(sqlType, Object.class);
    }

    public static int toSqlType(Class<?> connIdType) {
        if (String.class.equals(connIdType)) {
            return Types.VARCHAR;
        } else if (Integer.class.equals(connIdType) || int.class.equals(connIdType)) {
            return Types.INTEGER;
        } else if (Long.class.equals(connIdType) || long.class.equals(connIdType)) {
            return Types.BIGINT;
        } else if (Boolean.class.equals(connIdType) || boolean.class.equals(connIdType)) {
            return Types.BOOLEAN;
        } else if (Double.class.equals(connIdType) || double.class.equals(connIdType)) {
            return Types.DOUBLE;
        } else if (Float.class.equals(connIdType) || float.class.equals(connIdType)) {
            return Types.REAL;
        } else if (Byte.class.equals(connIdType) || byte.class.equals(connIdType)) {
            return Types.TINYINT;
        } else if (Short.class.equals(connIdType) || short.class.equals(connIdType)) {
            return Types.SMALLINT;
        } else if (java.sql.Date.class.equals(connIdType)) {
            return Types.DATE;
        } else if (java.sql.Time.class.equals(connIdType)) {
            return Types.TIME;
        } else if (java.sql.Timestamp.class.equals(connIdType)) {
            return Types.TIMESTAMP;
        } else if (byte[].class.equals(connIdType)) {
            return Types.VARBINARY;
        } else if (Object.class.equals(connIdType)) {
            return Types.OTHER;
        }
        return Types.VARCHAR;
    }

    public static Object toConnIdValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType == null || targetType.isAssignableFrom(value.getClass())) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class) {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        } else if (targetType == Long.class) {
            return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
        } else if (targetType == Double.class) {
            return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
        } else if (targetType == Boolean.class) {
            return value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
        }
        return value;
    }
}