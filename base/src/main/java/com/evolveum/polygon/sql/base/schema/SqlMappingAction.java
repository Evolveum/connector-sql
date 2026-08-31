/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;

import java.util.function.Consumer;

/**
 * A thin binding of conndev's shared {@link MappingAction} to SQL's concrete types — every
 * {@link SqlResourceMappingRule}/{@link SqlAttributeMappingRule} action returns this, since both
 * bind the same {@code OC}/{@code A}/{@code H} type arguments.
 */
public interface SqlMappingAction extends MappingAction<
        SqlObjectClassSchemaBuilder, SqlAttributeBuilder<SqlAttributeBuilder.Reference>, SqlObjectOperationBuilderImpl> {

    /** An action that only touches the attribute builder, built from a plain consumer. */
    static SqlMappingAction attribute(Consumer<SqlAttributeBuilder<SqlAttributeBuilder.Reference>> applier) {
        return new SqlMappingAction() {
            @Override
            public void applyToAttribute(SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
                applier.accept(attribute);
            }
        };
    }
}
