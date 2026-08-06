/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilderImpl;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;

import java.util.function.Consumer;

/**
 * Represents a detected property from SQL schema metadata that affects
 * both schema definitions and handler configurations.
 * <p>
 * Actions are created by {@link SchemaMappingRule} implementations and
 * applied during translation. Schema effects are applied immediately during
 * translation, while handler effects are collected for post-translation application
 * in the connector.
 */
public interface SchemaMappingAction {

    /**
     * Apply schema effects to the object class and optionally an attribute.
     *
     * @param objectClass the object class builder
     */
    default void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass) {
    }

    /**
     * Apply handler effects to the object class handler builder.
     * Schema-only actions may leave this empty.
     *
     * @param handlerBuilder the operation support builder for this object class
     */
    default void applyToHandlers(SqlObjectOperationBuilderImpl handlerBuilder) {
    }

    /**
     * Creates a column-specific detection action that applies a transformation to the
     * SQL attribute builder associated with the specified column. If attribute does not exists, it creates it.
     *
     * @param column the SQL column metadata to target
     * @param transform the consumer that transforms the SQL attribute builder
     * @return a column-specific detection action instance
     */
    static SchemaMappingAction.ColumnSpecific attributeSpecific(SqlColumnMeta column, Consumer<SqlAttributeBuilderImpl> tranform) {
        return new AttributeByColumnAction(column, tranform);
    }

    interface ColumnSpecific extends SchemaMappingAction {

        SqlColumnMeta column();

        void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass, SqlAttributeBuilderImpl attribute);

        @Override
        default void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass) {
            var maybeAttribute = objectClass.findAttributes(attr -> attr.sql().column().isPresent() &&
                    attr.sql().column().value().equals(column().getName()));

            if (maybeAttribute.isEmpty()) {
                applyToSchema(objectClass, objectClass.reference(DefinitionValue.detected(column().getName())));
            } else {
                applyToSchema(objectClass, (SqlAttributeBuilderImpl) maybeAttribute.iterator().next());
            }
        }
    }

    record AttributeByColumnAction(SqlColumnMeta column, Consumer<SqlAttributeBuilderImpl> tranform) implements ColumnSpecific {

        @Override
        public void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass, SqlAttributeBuilderImpl attribute) {
            tranform.accept(attribute);
        }
    }
}
