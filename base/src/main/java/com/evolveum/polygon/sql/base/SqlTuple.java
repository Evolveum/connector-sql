package com.evolveum.polygon.sql.base;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.sql.RelationalPathBase;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;


public record SqlTuple(RelationalPathBase<?> table, Tuple row, Map<String, Object> nameValues) {

    public SqlTuple(RelationalPathBase<?> table, Tuple row) {
        this(table, row, null);
    }

    public <T> T get(Path<T> path) {
        String columnName = getColumnName(path);
        if (nameValues != null && nameValues.containsKey(columnName)) {
            @SuppressWarnings("unchecked")
            var value = (T) nameValues.get(columnName);
            return value;
        }
        return row().get(path);
    }

    static String getColumnName(Path<?> path) {
        var p = path.toString();
        int lastDot = p.lastIndexOf('.');
        if (lastDot >= 0) {
            return p.substring(lastDot + 1);
        }
        return p;
    }

    public static SqlTuple of(Map<String, Object> map, String... columnNames) {
        if (columnNames.length == 0) {
            Map<String, Object> values = new TreeMap<>(map);
            return new SqlTuple(null, null, values);
        }
        Map<String, Object> values = new TreeMap<>();
        for (String name : columnNames) {
            if (map.containsKey(name)) {
                values.put(name, map.get(name));
            }
        }
        if (values.isEmpty()) {
            return new SqlTuple(null, null, Collections.emptyMap());
        }
        return new SqlTuple(null, null, values);
    }
}
