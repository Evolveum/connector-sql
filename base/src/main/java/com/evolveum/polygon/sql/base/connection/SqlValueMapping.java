/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.conndev.spi.ValueMapping;
import com.querydsl.core.types.Path;

import java.sql.JDBCType;

/**
 * Represents a mapping between ConnId types and SQL wire types.
 * Extends the ValueMapping interface from connector-scimrest.
 */
public interface SqlValueMapping extends ValueMapping<Object, Object> {

    /**
     * Looks up a value mapping by JDBC type code (from {@code DatabaseMetaData}).
     * Uses {@link SqlSchemaValueMapping#fromJdbcType(int)} which gracefully returns null
     * for unknown type codes, unlike {@code JDBCType.valueOf()} which throws.
     */
    static SqlValueMapping from(int typeCode) {
        return SqlSchemaValueMapping.fromJdbcType(typeCode);
    }

    Class<?> connIdType();

    Class<?> primaryWireType();

    interface SingleColumn extends SqlValueMapping {

        Path<?> pathFor(Path<?> parent, String column);

    }

    interface MultiColumn extends SqlValueMapping {


    }
}
