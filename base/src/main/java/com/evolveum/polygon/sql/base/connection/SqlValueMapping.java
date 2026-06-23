/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import java.util.Set;

/**
 * Represents a mapping between ConnId types and SQL wire types.
 * Extends the ValueMapping interface from connector-scimrest.
 *
 * @param <C> ConnId type
 * @param <P> SQL wire type (e.g., String, Integer, JsonNode for JSONB)
 */
public interface SqlValueMapping<C, P> {

    Class<? extends C> connIdType();
    
    Class<? extends P> primaryWireType();
    
    default Set<Class<? extends P>> supportedWireTypes() {
        return Set.of(primaryWireType());
    }

    P toWireValue(C value) throws IllegalArgumentException;

    C toConnIdValue(P value) throws IllegalArgumentException;

}
