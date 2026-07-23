/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;


import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.Expressions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;

import static com.evolveum.polygon.conndev.spi.ValueMapping.conversion;
import static com.evolveum.polygon.sql.base.connection.QueryDslUtils.*;
/**
 * Maps Java types used by QueryDSL to ConnId types with bidirectional conversion.
 * <p>
 * Each constant defines
 * <ul>
 *   <li>the Java type QueryDSL reads from SQL (querydslJavaType),</li>
 *   <li>the Java type QueryDSL writes to SQL (outputJavaType),</li>
 *   <li>the ConnId type exposed to the connector API (connIdType).</li>
 * </ul>
 * </p>
 * <p>
 * Analogous to {@code JsonSchemaValueMapping} in the conndev-base framework,
 * but uses Java types (sql date/time/numeric) instead of SQL type codes.
 * </p>
 */
public record QueryDslTypeMapping<C,P> (ValueMapping<C,P> mapping, BiFunction<Path<?>, String, ? extends Path<P>> pathFunction) implements SqlValueMapping.SingleColumn {

    public static QueryDslTypeMapping<String, String> STRING = identity(String.class, Expressions::stringPath);
    public static QueryDslTypeMapping<Integer, Integer> INTEGER = identity(Integer.class, numberPath(Integer.class));
    public static QueryDslTypeMapping<Integer, Short> SMALL_INT = from(conversion(Integer.class, Short.class, Short::intValue, Integer::shortValue), numberPath(Short.class));
    public static QueryDslTypeMapping<BigInteger, BigInteger> BIG_INT = identity(BigInteger.class, numberPath(BigInteger.class));
    public static QueryDslTypeMapping<BigDecimal, BigDecimal> DECIMAL = identity(BigDecimal.class, numberPath(BigDecimal.class));
    public static QueryDslTypeMapping<Double, Float> FLOAT_NUM = from(conversion(Double.class, Float.class, Float::doubleValue, Double::floatValue), numberPath(Float.class));
    public static QueryDslTypeMapping<Double, Double> DOUBLE = identity(Double.class, numberPath(Double.class));
    public static QueryDslTypeMapping<Boolean, Boolean> BOOLEAN = identity(Boolean.class, Expressions::booleanPath);
    // TODO: Is this correct represantation of default mapping (ConnId doesnt support only date or only time
    public static QueryDslTypeMapping<ZonedDateTime, Date> SQL_DATE = from(conversion(ZonedDateTime.class, Date.class,
                    p -> p.toLocalDate().atStartOfDay(ZoneId.systemDefault()),
                    c -> Date.valueOf(c.toLocalDate())) ,
            datePath(Date.class));

    public static QueryDslTypeMapping<String, LocalTime> SQL_TIME = from(conversion(String.class, LocalTime.class, LocalTime::toString, LocalTime::parse), timePath(LocalTime.class));
    public static QueryDslTypeMapping<ZonedDateTime, Timestamp> SQL_TIMESTAMP = from(conversion(ZonedDateTime.class, Timestamp.class, t -> t.toInstant().atZone(ZoneId.systemDefault()),
            t -> Timestamp.from(t.toInstant())), dateTimePath(Timestamp.class));

    public static QueryDslTypeMapping<ZonedDateTime, ZonedDateTime> SQL_TIMESTAMP_TZ = identity(ZonedDateTime.class, dateTimePath(ZonedDateTime.class));
    public static QueryDslTypeMapping<byte[], byte[]> BYTE_ARRAY = identity(byte[].class, QueryDslUtils::byteArrayPath);



    public static <P> QueryDslTypeMapping<P, P> identity(Class<P> clazz, BiFunction<Path<?>, String, ? extends Path<P>> pathFunction) {
        return new QueryDslTypeMapping<>(ValueMapping.identity(clazz), pathFunction);
    }

    public static <C,P> QueryDslTypeMapping<C,P> from(ValueMapping<C, P> delegate, BiFunction<Path<?>, String, ? extends Path<P>> pathFunction) {
        return new QueryDslTypeMapping<>(delegate, pathFunction);
    }

    @Override
    public Object toWireValue(Object value) throws IllegalArgumentException {
        return mapping.toWireValue(ValueMapping.checkValueInstanceOf(mapping.connIdType(),value));
    }

    @Override
    public Object toConnIdValue(Object value) throws IllegalArgumentException {
        return mapping.toConnIdValue(ValueMapping.checkValueInstanceOf(mapping.primaryWireType(),value));
    }

    @Override
    public Class<?> primaryWireType() {
        return mapping.primaryWireType();
    }

    @Override
    public Class<?> connIdType() {
        return mapping.connIdType();
    }

    @Override
    public Path<?> pathFor(Path<?> parent, String column) {
        return pathFunction.apply(parent, column);
    }
}
