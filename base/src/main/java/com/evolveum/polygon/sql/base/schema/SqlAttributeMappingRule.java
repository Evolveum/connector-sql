/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;

/**
 * Strategy for detecting properties from SQL column-level metadata.
 * <p>
 * A thin binding of conndev's shared {@link MappingRule} to SQL's concrete types — the context is
 * a {@link Context} (table + column).
 */
public interface SqlAttributeMappingRule extends MappingRule<
        SqlAttributeMappingRule.Context,
        SqlObjectClassSchemaBuilder,
        SqlAttributeBuilder<SqlAttributeBuilder.Reference>,
        SqlObjectOperationBuilderImpl> {

    /**
     * The context an attribute-level SQL rule needs: the table it belongs to, and the column it
     * describes. Bundled into one record so this rule fits conndev's shared
     * {@code MappingRule<C, OC, A, H>} shape (a single context type), rather than carrying two
     * separate context parameters.
     */
    record Context(SqlTableInfo table, SqlColumnMeta column) {
    }
}
