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
 * Detects auto-increment columns and marks them as non-creatable and non-updatable.
 * <p>
 * Schema effects:
 * <ul>
 *   <li>Sets {@code creatable(false)} and {@code updatable(false)} on the attribute</li>
 * </ul>
 * Handler effects: none (attribute-level constraint, not object-class-level)
 * <p>
 * Applies only at the column level ({@code column != null}).
 */
public class AutoIncrementColumnIsNotEditableRule implements SchemaMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        return column != null && column.isAutoIncrement();
    }

    @Override
    public SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        return SchemaMappingAction.attributeSpecific(column, attribute -> {
            attribute.connId().creatable(detected(false));
            attribute.connId().updatable(detected(false));
        });
    }
}
