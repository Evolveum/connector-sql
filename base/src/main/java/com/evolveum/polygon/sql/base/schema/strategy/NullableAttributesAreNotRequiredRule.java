/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.SchemaMappingAction;
import com.evolveum.polygon.sql.base.schema.SchemaMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

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
public class NullableAttributesAreNotRequiredRule implements SchemaMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        return column != null;
    }

    @Override
    public SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        return SchemaMappingAction.attributeSpecific(column,
                attr -> attr.connId().required(detected(!column.isNullable())));
    }
}
