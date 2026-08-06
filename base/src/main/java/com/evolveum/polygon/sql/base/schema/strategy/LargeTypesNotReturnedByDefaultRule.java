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
 * Detects large column types (BLOB, CLOB, BINARY, VARBINARY)
 * and marks them as not returned by default.
 * <p>
 * Schema effects:
 * <ul>
 *   <li>Sets {@code returnedByDefault(false)} on the attribute</li>
 * </ul>
 * Handler effects: none
 */
public class LargeTypesNotReturnedByDefaultRule implements SchemaMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        if (column == null) {
            return false;
        }
        return isLargeType(column.getTypeName());
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
    public SchemaMappingAction.ColumnSpecific createAction(SqlTableInfo table, SqlColumnMeta column) {
        return SchemaMappingAction.attributeSpecific(column,
                attribute -> attribute.connId().returnedByDefault(detected(false)));
    }

}
