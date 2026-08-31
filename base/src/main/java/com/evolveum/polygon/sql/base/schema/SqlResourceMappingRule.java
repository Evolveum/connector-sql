/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.concepts.MappingAction;
import com.evolveum.polygon.conndev.concepts.MappingRule;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;

/**
 * Strategy for detecting properties from SQL table-level metadata.
 * <p>
 * A thin binding of conndev's shared {@link MappingRule} to SQL's concrete types — the context is
 * a {@link SqlTableInfo}. A rule whose action also has a handler effect overrides
 * {@link MappingAction#applyToHandler}, re-applied later, once handlers exist — see
 * {@code SqlSchemaTranslator#applyHandlerRulesFor}.
 */
public interface SqlResourceMappingRule extends MappingRule<
        SqlTableInfo,
        SqlObjectClassSchemaBuilder,
        SqlAttributeBuilder<SqlAttributeBuilder.Reference>,
        SqlObjectOperationBuilderImpl> {
}
