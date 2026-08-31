/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;


import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.schema.SqlAttributeMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlMappingAction;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Detects auto-increment columns and marks them as non-creatable and non-updatable.
 * <p>
 * Schema effects:
 * <ul>
 *   <li>Sets {@code creatable(false)} and {@code updatable(false)} on the attribute</li>
 * </ul>
 * Handler effects: none (attribute-level constraint, not object-class-level)
 */
public class AutoIncrementColumnIsNotEditableRule implements SqlAttributeMappingRule {

    @Override
    public boolean checkIfApplicable(Context context, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        return context.column().isAutoIncrement();
    }

    @Override
    public SqlMappingAction createAction(Context context) {
        return SqlMappingAction.attribute(attribute -> {
            attribute.connId().creatable(detected(false));
            attribute.connId().updatable(detected(false));
        });
    }
}
