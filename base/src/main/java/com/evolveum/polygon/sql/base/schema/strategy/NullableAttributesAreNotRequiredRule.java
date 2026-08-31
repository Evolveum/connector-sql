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
 * Detects nullable column constraints and sets the required flag accordingly.
 * <p>
 * Schema effects:
 * <ul>
 *   <li>Sets {@code required(!nullable)} on the attribute</li>
 * </ul>
 * Handler effects: none
 */
public class NullableAttributesAreNotRequiredRule implements SqlAttributeMappingRule {

    @Override
    public boolean checkIfApplicable(Context context, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        return true;
    }

    @Override
    public SqlMappingAction createAction(Context context) {
        return SqlMappingAction.attribute(attribute -> attribute.connId().required(detected(!context.column().isNullable())));
    }
}
