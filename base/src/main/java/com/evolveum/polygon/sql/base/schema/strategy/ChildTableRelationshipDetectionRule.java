/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilderImpl;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;
import com.evolveum.polygon.sql.base.schema.*;
import com.evolveum.polygon.sql.base.search.SqlJoinAttributeResolver;
import com.evolveum.polygon.sql.base.search.SqlJunctionAttributeResolver;

import java.util.List;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Detection rule that processes child table relationships.
 * Applies to both parent tables (add embedded/reference attributes) and child tables (mark as embedded).
 */
public class ChildTableRelationshipDetectionRule implements SchemaMappingRule {

    private final SqlSchemaTranslator translator;

    public ChildTableRelationshipDetectionRule(SqlSchemaTranslator translator) {
        this.translator = translator;
    }

    @Override
    public boolean checkIfApplicable(SqlTableInfo table, SqlColumnMeta column) {
        if (column != null) {
            return false;
        }
        var tableName = table.getName();
        var rels = translator.getTableRelationships(tableName);
        boolean hasChildRels = rels.stream().anyMatch(r -> r.type().isEmbedded());
        boolean hasSimpleAttributeRels = rels.stream().anyMatch(r -> r.type().isSimpleAttribute());
        boolean isEmbeddedChild = translator.getEmbeddedChildTables().contains(tableName.toUpperCase());
        boolean hasJunctionRels = rels.stream().anyMatch(r -> r.type().isJunction());
        return hasChildRels || hasSimpleAttributeRels || isEmbeddedChild || hasJunctionRels;
    }

    @Override
    public SchemaMappingAction createAction(SqlTableInfo table, SqlColumnMeta column) {
        var tableName = table.getName();
        if (translator.getEmbeddedChildTables().contains(tableName.toUpperCase())) {
            return new ChildEmbeddedAction(table);
        }
        var rels = translator.getTableRelationships(tableName);
        if (!rels.isEmpty()) {
            return new ParentEmbeddedAction(table, rels, translator);
        }
        return null;
    }

    /** Marks a child table's object class as embedded. */
    private static class ChildEmbeddedAction implements SchemaMappingAction {

        private final SqlTableInfo childTable;

        ChildEmbeddedAction(SqlTableInfo t) {
            this.childTable = t;
        }

        @Override
        public void applyToSchema(SqlObjectClassSchemaBuilderImpl oc) {
            oc.embedded(detected(true));
        }
    }

    /** Adds attributes to parent table and registers resolvers for child data. */
    private static class ParentEmbeddedAction implements SchemaMappingAction {

        private final SqlTableInfo parentTable;
        private final List<ChildTableRelationship> relationships;
        private final SqlSchemaTranslator translator;

        ParentEmbeddedAction(SqlTableInfo t, List<ChildTableRelationship> rels, SqlSchemaTranslator tr) {
            this.parentTable = t;
            this.relationships = rels;
            this.translator = tr;
        }

        @Override
        public void applyToSchema(SqlObjectClassSchemaBuilderImpl objectClass) {
            for (ChildTableRelationship rel : relationships) {
                if (rel.type().isEmbedded()) {
                    addEmbeddedAttribute(objectClass, rel);
                } else if (rel.type().isSimpleAttribute()) {
                    addSimpleAttribute(objectClass, rel);
                } else {
                    var jr = (ChildTableRelationship.JunctionRelationship) rel;
                    addReferenceAttribute(objectClass, jr);
                }
            }
        }

        @Override
        public void applyToHandlers(SqlObjectOperationBuilderImpl handlerBuilder) {
            for (ChildTableRelationship rel : relationships) {
                if (rel.type().isEmbedded()) {
                    registerEmbeddedResolver(handlerBuilder, rel);
                } else if (rel.type().isSimpleAttribute()) {
                    registerSimpleAttributeResolver(handlerBuilder, rel);
                } else {
                    var jr = (ChildTableRelationship.JunctionRelationship) rel;
                    registerJunctionResolver(handlerBuilder, jr);
                }
            }
        }

        private void addSimpleAttribute(SqlObjectClassSchemaBuilderImpl objectClass,
                                        ChildTableRelationship rel) {
            var attrName = rel.childTable();
            var sar =
                    (ChildTableRelationship.SimpleAttributeRelationship) rel;
            var attr = (SqlAttributeBuilderImpl) objectClass.attribute(attrName);
            // Use value column's type mapping for the attribute type
            var valueCol = sar.valueColumn();
            if (valueCol != null && valueCol.getValueMapping() != null) {
                attr.connId().type(detected(valueCol.getValueMapping().connIdType()));
            } else {
                attr.connId().type(String.class);
            }
            attr.connId().multiValued(detected(true));
            objectClass.addEmbeddedJoinConfig(createSimpleAttributeJoinConfig(rel));
        }

        private void registerSimpleAttributeResolver(SqlObjectOperationBuilderImpl hBuilder,
                                                     ChildTableRelationship rel) {
            var config = createSimpleAttributeJoinConfig(rel);
            var searchBuilder = hBuilder.search();
            var contextLookup = resolveContextLookup(hBuilder);
            var resolver = new SqlJoinAttributeResolver(
                    contextLookup != null ? (SqlBaseContext) contextLookup : null,
                    config, rel.childTable());
            searchBuilder.registerSqlResolver(resolver);
        }

        private void addEmbeddedAttribute(SqlObjectClassSchemaBuilderImpl objectClass,
                                          ChildTableRelationship rel) {
            var attrName = rel.childTable();
            boolean multiValued = !rel.type().isSingleValue();
            var attr = (SqlAttributeBuilderImpl) objectClass.attribute(attrName);
            attr.complexType(detected(rel.childTable()));
            attr.connId().multiValued(detected(multiValued));
            objectClass.addEmbeddedJoinConfig(createSqlJoinConfig(rel));
        }

        private void addReferenceAttribute(SqlObjectClassSchemaBuilderImpl objectClass,
                                           ChildTableRelationship.JunctionRelationship jr) {
            var targetTable = jr.targetTable();
            var ref = (SqlAttributeBuilderImpl) objectClass.reference(detected(targetTable));
            ref.objectClass(targetTable);
            ref.connId().multiValued(detected(true));
            objectClass.addJunctionJoinConfig(createJunctionConfig(jr));
        }

        private void registerEmbeddedResolver(SqlObjectOperationBuilderImpl hBuilder,
                                              ChildTableRelationship rel) {
            var config = createSqlJoinConfig(rel);
            var searchBuilder = hBuilder.search();
            var contextLookup = resolveContextLookup(hBuilder);
            var resolver = new SqlJoinAttributeResolver(
                    contextLookup != null ? (SqlBaseContext) contextLookup : null,
                    config, rel.childTable());
            searchBuilder.registerSqlResolver(resolver);
        }

        private void registerJunctionResolver(SqlObjectOperationBuilderImpl hBuilder,
                                              ChildTableRelationship.JunctionRelationship jr) {
            var config = createJunctionConfig(jr);
            var searchBuilder = hBuilder.search();
            var contextLookup = resolveContextLookup(hBuilder);
            var resolver = new SqlJunctionAttributeResolver(
                    contextLookup != null ? (SqlBaseContext) contextLookup : null,
                    config, jr.targetTable());
            searchBuilder.registerSqlResolver(resolver);
        }

        @SuppressWarnings("unchecked")
        private ContextLookup resolveContextLookup(SqlObjectOperationBuilderImpl hBuilder) {
            if (hBuilder instanceof SqlObjectOperationBuilderImpl impl) {
                return impl.getContext();
            }
            return null;
        }

        private SqlChildJoinConfig createSqlJoinConfig(ChildTableRelationship rel) {
            var jk = rel.joinKeys().getFirst();
            return new SqlChildJoinConfig(
                    rel.childTable(), jk.parentColumn(), jk.childColumn(),
                    !rel.type().isSingleValue(), rel.childTable());
        }

        private SqlChildJoinConfig createSimpleAttributeJoinConfig(ChildTableRelationship rel) {
            var jk = rel.joinKeys().getFirst();
            var sar =
                    (ChildTableRelationship.SimpleAttributeRelationship) rel;
            String valueCol = sar.valueColumn() != null ? sar.valueColumn().getName() : null;
            return new SqlChildJoinConfig(
                    rel.childTable(), jk.parentColumn(), jk.childColumn(),
                    true, rel.childTable(), valueCol);
        }

        private SqlJunctionJoinConfig createJunctionConfig(ChildTableRelationship.JunctionRelationship jr) {
            return new SqlJunctionJoinConfig(
                    jr.junctionTable(),
                    jr.parentJoinKeys().getFirst().parentColumn(),
                    jr.parentJoinKeys().getFirst().childColumn(),
                    jr.targetJoinKeys().getFirst().childColumn(),
                    jr.targetTable()
            );
        }
    }
}
