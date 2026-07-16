package com.evolveum.polygon.sql.base;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.sql.RelationalPathBase;


public record SqlTuple(RelationalPathBase<?> table, Tuple row) {

    public <T> T get(Path<T> path) {
        return row().get(path);
    }
}
