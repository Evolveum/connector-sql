/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;

public class SqlAttributeDefinition extends BaseAttributeDefinition {

    private SqlAttributeMapping sql;

    /**
     * Constructs a {@code SqlAttributeDefinition} from the supplied builder, performing type
     * resolution across protocol mappings and building the final {@link org.identityconnectors.framework.common.objects.AttributeInfo}.
     *
     * <p>{@code super(builder)} resolves and freezes the final ConnId type first; only then is
     * {@link SqlAttributeMapping#withConnIdType(Class)} applied, so its wire-type-conversion
     * decision (e.g. Integer column exposed as a String UID) is based on the definitive type,
     * not whatever {@code connId().type()} happened to hold mid-construction.
     *
     * @param builder the {@code SqlAttributeBuilderImpl} providing all metadata for this attribute
     * @throws IllegalStateException    if multiple protocol mappings declare conflicting ConnId types
     * @throws IllegalArgumentException if no ConnId type can be resolved for a non-reference attribute
     */
    public SqlAttributeDefinition(SqlAttributeBuilderImpl builder) {
        super(builder);
        var rawMapping = builder.sql().build();
        this.sql = rawMapping != null ? rawMapping.withConnIdType(this.connId().getType()) : null;
    }

    /**
     * Returns the SQL attribute mapping for this attribute, constructing it lazily from
     * the definition's own properties the first time it's called.
     * <pre>
     *   connId()      → getConnIdName()
     *   remoteName()  → getSqlColumn()
     *   connId()      → isReturnedByDefault()
     * </pre>
     */
    public SqlAttributeMapping sql() {
        return this.sql;
    }

}
