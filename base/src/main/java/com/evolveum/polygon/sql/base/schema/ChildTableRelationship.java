/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import java.util.List;

/**
 * Represents a detected child table relationship between SQL tables.
 * Sealed interface with two implementations:
 * - {@link EmbeddedRelationship}: child table becomes an embedded object on the parent
 * - {@link JunctionRelationship}: junction table creates a reference between two tables
 */
public sealed interface ChildTableRelationship permits
        ChildTableRelationship.EmbeddedRelationship,
        ChildTableRelationship.SimpleAttributeRelationship,
        ChildTableRelationship.JunctionRelationship {

    String parentTable();
    String childTable();
    List<JoinKey> joinKeys();
    ChildTableType type();
    boolean conventionBased();

    /**
     * Attribute type for the value column when type is {@link ChildTableType#MULTI_VALUE_ATTRIBUTE}.
     * Returns {@code null} for embedded/junction relationships.
     */
    default SqlColumnMeta valueColumn() { return null; }

    /**
     * A single join key mapping a parent column to a child column.
     */
    record JoinKey(String parentColumn, String childColumn) {}

    /**
     * Classification of the child table relationship.
     */
    enum ChildTableType {
        /** Child PK equals FK only — single-valued embedded object */
        SINGLE_VALUE_EMBEDDED,
        /** Child has PK with FK + data columns — multi-valued embedded objects */
        MULTI_VALUE_EMBEDDED,
        /** Child has PK with FK + exactly one data column — multi-valued attribute (simple type) */
        MULTI_VALUE_ATTRIBUTE,
        /** Table has FKs to 2+ different tables — bidirectional references */
        JUNCTION_TABLE;

        public boolean isEmbedded() {
            return this == SINGLE_VALUE_EMBEDDED || this == MULTI_VALUE_EMBEDDED;
        }

        public boolean isSimpleAttribute() {
            return this == MULTI_VALUE_ATTRIBUTE;
        }

        public boolean isJunction() {
            return this == JUNCTION_TABLE;
        }

        public boolean isSingleValue() {
            return this == SINGLE_VALUE_EMBEDDED;
        }

        public boolean isMultiValue() {
            return this != SINGLE_VALUE_EMBEDDED && this != JUNCTION_TABLE;
        }
    }

    /**
     * Embedded relationship: child table columns become embedded object attributes on parent.
     */
    record EmbeddedRelationship(
            String parentTable,
            String childTable,
            List<JoinKey> joinKeys,
            ChildTableType type,
            boolean conventionBased
    ) implements ChildTableRelationship {}

    /**
     * Simple attribute relationship: child table has FK + exactly one value column.
     * Creates a simple multivalue attribute (not embedded) on the parent.
     */
    record SimpleAttributeRelationship(
            String parentTable,
            String childTable,
            List<JoinKey> joinKeys,
            SqlColumnMeta valueColumn,
            ChildTableType type,
            boolean conventionBased
    ) implements ChildTableRelationship {
        public SimpleAttributeRelationship {
            if (type != ChildTableType.MULTI_VALUE_ATTRIBUTE) {
                throw new IllegalArgumentException("Must be MULTI_VALUE_ATTRIBUTE");
            }
        }

        @Override
        public SqlColumnMeta valueColumn() {
            return valueColumn;
        }
    }

    /**
     * Junction relationship: junction table creates a bidirectional reference between two tables.
     */
    record JunctionRelationship(
            String parentTable,
            String junctionTable,
            List<JoinKey> parentJoinKeys,
            List<JoinKey> targetJoinKeys,
            String targetTable,
            ChildTableType type,
            boolean conventionBased
    ) implements ChildTableRelationship {
        @Override
        public String childTable() {
            return junctionTable;
        }

        @Override
        public List<JoinKey> joinKeys() {
            return parentJoinKeys;
        }
    }
}
