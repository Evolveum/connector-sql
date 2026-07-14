package com.evolveum.polygon.sql.base.connection;

import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.ArrayPath;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.Expressions;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.function.BiFunction;

public class QueryDslUtils {

    public static ArrayPath<byte[], Byte> byteArrayPath(Path<?> path, String name) {
        return Expressions.arrayPath(byte[].class, path, name);
    }

    public static BiFunction<Path<?>, String, Path<?>> dateTimePath(Class<? extends Comparable<?>> dateClass) {
        return (path, name) -> Expressions.dateTimePath(dateClass, path, name);
    }

    public static DateTimePath<Timestamp>  timestampPath(Path<?> path, String name) {
        return Expressions.dateTimePath(Timestamp.class, path, name);
    }

    public static BiFunction<Path<?>, String, Path<?>> timePath(Class<Time> timeClass) {
        return  (path, name) -> Expressions.timePath(timeClass, path, name);
    }

    public static <T extends Number & Comparable<?>> BiFunction<Path<?>, String, Path<?>>  numberPath(Class<T> number) {
        return (path, name) -> Expressions.numberPath(number, path, name);
    }
}
