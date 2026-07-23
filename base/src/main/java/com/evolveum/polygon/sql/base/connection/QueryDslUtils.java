package com.evolveum.polygon.sql.base.connection;

import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.*;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.function.BiFunction;

public class QueryDslUtils {

    public static ArrayPath<byte[], Byte> byteArrayPath(Path<?> path, String name) {
        return Expressions.arrayPath(byte[].class, path, name);
    }

    public static <T extends Comparable<?>> BiFunction<Path<?>, String, DateTimePath<T>> dateTimePath(Class<T> dateClass) {
        return (path, name) -> Expressions.dateTimePath(dateClass, path, name);
    }

    public static <T extends Comparable<?>> BiFunction<Path<?>, String, DatePath<T>> datePath(Class<T> dateClass) {
        return (path, name) -> Expressions.datePath(dateClass, path, name);
    }

    public static DateTimePath<Timestamp>  timestampPath(Path<?> path, String name) {
        return Expressions.dateTimePath(Timestamp.class, path, name);
    }

    public static <T  extends Comparable<?>> BiFunction<Path<?>, String, TimePath<T>> timePath(Class<T> timeClass) {
        return  (path, name) -> Expressions.timePath(timeClass, path, name);
    }

    public static <T extends Number & Comparable<?>> BiFunction<Path<?>, String, Path<T>>  numberPath(Class<T> number) {
        return (path, name) -> Expressions.numberPath(number, path, name);
    }
}
