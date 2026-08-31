/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SqlMappingAction;
import com.evolveum.polygon.sql.base.schema.SqlResourceMappingRule;
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
public class ViewsShouldBeReadOnlyRule implements SqlResourceMappingRule {

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute) {
        return "VIEW".equalsIgnoreCase(table.getTableType());
    }

    @Override
    public SqlMappingAction createAction(SqlTableInfo table) {
        return new SqlMappingAction() {
            @Override
            public void applyToSchema(SqlObjectClassSchemaBuilder objectClass) {
                objectClass.readOnly(detected(true));
                if (Boolean.TRUE.equals(objectClass.getReadOnly())) {
                    objectClass.findAttributes(attr -> true).forEach(attribute -> {
                        attribute.connId().creatable(detected(false));
                        attribute.connId().updatable(detected(false));
                    });
                }
            }

            @Override
            public void applyToHandler(SqlObjectOperationBuilderImpl handlerBuilder) {
                if (Boolean.TRUE.equals(handlerBuilder.getObjectClass().getReadOnly())) {
                    handlerBuilder.create().enabled(detected(false));
                    handlerBuilder.update().enabled(detected(false));
                    handlerBuilder.delete().enabled(detected(false));
                }
            }
        };
    }
}
