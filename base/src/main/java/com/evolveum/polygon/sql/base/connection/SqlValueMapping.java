/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.conndev.spi.ValueMapping;

import java.sql.JDBCType;
import java.util.Arrays;

/**
 * Represents a mapping between ConnId types and SQL wire types.
 * Extends the ValueMapping interface from connector-scimrest.
 */
public interface SqlValueMapping extends ValueMapping<Object, Object> {

    public static SqlValueMapping from(int typeCode) {
        var jdbcType = JDBCType.valueOf(typeCode);
        return Arrays.stream(SqlSchemaValueMapping.values()).filter(s -> jdbcType.equals(s.jdbcType())).findFirst().orElse(null);
    }

    Class<?> connIdType();
    
    Class<?> primaryWireType();
    
    JDBCType jdbcType();
}
