/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilderImpl;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilderImpl;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.List;

/**
 * Extends {@link SchemaMappingAction} to identify and apply UID column configuration.
 * <p>
 * UID strategies return actions implementing this interface. When
 * {@link #applyToSchema(SqlObjectClassSchemaBuilder, SqlAttributeBuilder)} is called
 * at table-level (attribute == null), the action:
 * <ol>
 *   <li>Finds the UID attribute by column name</li>
 *   <li>Renames its ConnId name to {@link Uid#NAME}</li>
 *   <li>Adds composite PK additional columns if applicable</li>
 * </ol>
 */
public interface UidDetectionAction extends SchemaMappingAction.ColumnSpecific {

    @Override
    default void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass, SqlAttributeBuilderImpl attribute) {
        // Only apply at table level (attribute == null)
        var uidColumn = column();
        // Rename the UID attribute's ConnId name
        attribute.connId().name(Uid.NAME);
        // Apply composite PK additional columns
        applyCompositePk(objectClass, attribute, getAdditionalPkColumns());
    }

    /**
     * Returns additional PK columns for composite UID mappings.
     * Empty list if this is a single-column UID.
     */
    default List<SqlColumnMeta> getAdditionalPkColumns() {
        return List.of();
    }

    default void applyUidRename(SqlObjectClassSchemaBuilder objectClass, SqlColumnMeta uidColumn) {
        var columnName = uidColumn.getName();
        var attr = objectClass.attribute(columnName);
        attr.connId().name(Uid.NAME);
    }

    default void applyCompositePk(SqlObjectClassSchemaBuilder objectClass, SqlAttributeBuilderImpl uidAttr,
                                  List<SqlColumnMeta> additionalPks) {
        if (additionalPks.isEmpty()) {
            return;
        }
        if (uidAttr instanceof SqlAttributeBuilder.Reference refAttr) {
            var sqlMapping = refAttr.sql();
            for (SqlColumnMeta pk : additionalPks) {
                if (pk.getValueMapping() != null) {
                    sqlMapping.additionalColumns().column(pk.getName(), pk.getValueMapping());
                }
            }
        }
    }
}
