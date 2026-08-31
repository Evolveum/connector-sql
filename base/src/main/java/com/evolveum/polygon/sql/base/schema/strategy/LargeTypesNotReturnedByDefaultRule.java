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
 * Detects large column types (BLOB, CLOB, BINARY, VARBINARY)
 * and marks them as not returned by default.
 * <p>
 * Schema effects:
 * <ul>
 *   <li>Sets {@code returnedByDefault(false)} on the attribute</li>
 * </ul>
 * Handler effects: none
 */
public class LargeTypesNotReturnedByDefaultRule implements SqlAttributeMappingRule {

    @Override
    public boolean checkIfApplicable(Context context, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        return isLargeType(context.column().getTypeName());
    }

    private boolean isLargeType(String typeName) {
        if (typeName == null) {
            return false;
        }
        var upper = typeName.toUpperCase();
        return upper.contains("BLOB") || upper.contains("CLOB")
                || upper.contains("BINARY") || upper.contains("VARBINARY");
    }

    @Override
    public SqlMappingAction createAction(Context context) {
        return SqlMappingAction.attribute(attribute -> attribute.connId().returnedByDefault(detected(false)));
    }

}
