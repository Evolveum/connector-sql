/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.List;

/**
 * Shared "found this column, make it the UID" logic used by the 4 UID-detection resource rules
 * ({@code SinglePrimaryKeyIsUidRule}, {@code ColumnsMatchingPatternAsUidRule},
 * {@code UniqueAttributeAsFallbackUidRule}, {@code CompositePkUidMappingRule}). Each rule's
 * {@code createAction} builds one of these (usually as a small record capturing the detected
 * column(s)) and returns it directly.
 */
public interface UidDetectionAction extends SqlMappingAction {

    /** The column this action identifies as the UID (or the primary composite-PK column). */
    SqlColumnMeta column();

    /**
     * Looks up the already-created attribute builder for {@link #column()} by name (every
     * column already has an attribute builder by the time resource-level rules run — see
     * {@code SqlSchemaTranslator#applyRulesFor}), then:
     * <ol>
     *   <li>Renames its ConnId name to {@link Uid#NAME}</li>
     *   <li>Adds composite PK additional columns if applicable</li>
     * </ol>
     */
    @Override
    default void applyToSchema(SqlObjectClassSchemaBuilder objectClass) {
        var uidColumn = column();
        var maybeAttribute = objectClass.findAttributes(attr -> attr.sql().column().isPresent()
                && attr.sql().column().value().equals(uidColumn.getName()));
        SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute = maybeAttribute.isEmpty()
                ? objectClass.reference(uidColumn.getName())
                : maybeAttribute.iterator().next();
        attribute.connId().name(Uid.NAME);
        applyCompositePk(attribute, getAdditionalPkColumns());
    }

    /**
     * Returns additional PK columns for composite UID mappings.
     * Empty list if this is a single-column UID.
     */
    default List<SqlColumnMeta> getAdditionalPkColumns() {
        return List.of();
    }

    default void applyCompositePk(SqlAttributeBuilder<SqlAttributeBuilder.Reference> uidAttr,
                                  List<SqlColumnMeta> additionalPks) {
        if (additionalPks.isEmpty()) {
            return;
        }
        var sqlMapping = uidAttr.sql();
        for (SqlColumnMeta pk : additionalPks) {
            if (pk.getValueMapping() != null) {
                sqlMapping.additionalColumns().column(pk.getName(), pk.getValueMapping());
            }
        }
    }

    /** A UID detected from a single column, with no additional composite-PK columns. */
    record SingleColumnUidAction(SqlColumnMeta column) implements UidDetectionAction {
    }
}
