/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.dev.ConnDevAttribute;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;

import java.util.ArrayList;

public class SqlAttributeDefinition extends BaseAttributeDefinition {

    private static final String SQL_BLOCK = "sql";
    private static final String F_COLUMN = "column";
    private static final String F_TYPE = "type";

    private SqlAttributeMapping sql;

    /**
     * Constructs a {@code SqlAttributeDefinition} from the supplied builder, performing type
     * resolution across protocol mappings and building the final {@link org.identityconnectors.framework.common.objects.AttributeInfo}.
     *
     * <p>The SQL mapping is taken from the protocol mappings resolved by {@code super(builder)}
     * — built with the attribute's final ConnId type already applied by
     * {@code SqlMappingBuilder#build()} (pushed there by {@code AttributeTypeCoercionRule}), so
     * this definition's {@link #sql()} and its entry in the protocol-mappings map are the same
     * coerced instance (e.g. an Integer column exposed as a String UID).
     *
     * @param builder the {@code SqlAttributeBuilderImpl} providing all metadata for this attribute
     * @throws IllegalStateException    if multiple protocol mappings declare conflicting ConnId types
     * @throws IllegalArgumentException if no ConnId type can be resolved for a non-reference attribute
     */
    public SqlAttributeDefinition(SqlAttributeBuilderImpl builder) {
        super(builder);
        this.sql = mapping(SqlAttributeMapping.class);
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

    /**
     * Contributes the attribute-level {@code sql} block to the development-mode export: the mapped
     * column and its native SQL type, so the detected schema shows the column mapping (work
     * package #12486).
     */
    @Override
    public void contribute(ConnDevAttribute target) {
        if (sql == null) {
            return;
        }
        var attributes = new ArrayList<Attribute>();
        if (sql.column().isPresent()) {
            attributes.add(AttributeBuilder.build(F_COLUMN, sql.column().value()));
        }
        if (sql.nativeType().isPresent()) {
            attributes.add(AttributeBuilder.build(F_TYPE, sql.nativeType().value()));
        }
        if (!attributes.isEmpty()) {
            target.protocolSpecific(SQL_BLOCK, attributes);
        }
    }

}
