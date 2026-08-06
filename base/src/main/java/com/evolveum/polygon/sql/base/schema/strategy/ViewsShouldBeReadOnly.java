/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SchemaMappingAction;
import com.evolveum.polygon.sql.base.schema.SchemaMappingRule;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Detects SQL VIEW tables and marks them as read-only.
 *
 * <p>Schema effects:
 * <ul>
 *   <li>Sets {@code readOnly(true)} on the object class</li>
 *   <li>Marks all attributes as non-creatable and non-updatable</li>
 * </ul>
 * <p>Handler effects:
 * <ul>
 *   <li>Disables CREATE, UPDATE, DELETE operations</li>
 * </ul>
 *
 * <p>If {@code readOnly} is already set by a Groovy script (e.g., manually on a TABLE),
 * this strategy does not re-apply (Groovy takes precedence).
 */
public class ViewsShouldBeReadOnly implements SchemaMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        // Applicable at both table and column level
        return "VIEW".equalsIgnoreCase(table.getTableType());
    }

    @Override
    public SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        return column == null
                ? new ViewDetectionAction()
                : null;
    }

    /**
     * Table-level action: marks object class as read-only.
     */
    private static class ViewDetectionAction implements SchemaMappingAction {

        @Override
        public void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass) {
            objectClass.readOnly(detected(true));
            if (Boolean.TRUE.equals(objectClass.getReadOnly())) {
                objectClass.allAttributes().forEach(attribute -> {
                        attribute.connId().creatable(detected(false));
                        attribute.connId().updatable(detected(false));
                });
            }
        }

        @Override
        public void applyToHandlers(SqlObjectOperationBuilderImpl handlerBuilder) {
            if (Boolean.TRUE.equals(handlerBuilder.getObjectClass().getReadOnly())) {
                handlerBuilder.create().enabled(detected(false));
                handlerBuilder.update().enabled(detected(false));
                handlerBuilder.delete().enabled(detected(false));
            }
        }
    }
}
