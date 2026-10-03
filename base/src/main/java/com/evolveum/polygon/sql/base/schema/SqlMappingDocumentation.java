/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import java.util.List;

/**
 * Human-readable documentation for automatically detected (auto-mapped) object classes and
 * attributes: which table an object class maps to, which column an attribute maps to, and the
 * native column type (work package #12486 — the SQL connector has to show how object classes map
 * to SQL tables).
 *
 * <p>Callers set the produced text with a DETECTED {@code DefinitionValue}
 * ({@code DefinitionValue.detected(...)}), so an explicitly declared description (user script)
 * always takes precedence over the detected one via {@code moreSpecific}.</p>
 */
public final class SqlMappingDocumentation {

    private SqlMappingDocumentation() {
    }

    /** Object-class level: the table (and schema) this object class is mapped to. */
    public static String objectClass(SqlTableInfo table) {
        var builder = new StringBuilder("Mapped to ")
                .append(tableKind(table))
                .append(' ')
                .append(quote(table.getName()));
        if (table.getSchema() != null && !table.getSchema().isBlank()) {
            builder.append(" (schema ").append(quote(table.getSchema())).append(')');
        }
        return builder.toString();
    }

    /** Attribute level: the column of the given table this attribute is mapped to, with native type. */
    public static String attribute(SqlTableInfo table, SqlColumnMeta column) {
        return "Mapped to column "
                + quote(column.getName())
                + " of "
                + tableKind(table)
                + ' '
                + quote(table.getName())
                + nativeType(column);
    }

    /** Attribute level: embedded objects stored in a child table. */
    public static String embeddedChildTable(String childTable) {
        return "Mapped to embedded child table " + quote(childTable);
    }

    /** Attribute level: a multi-valued scalar attribute backed by a child table's value column. */
    public static String childTableValueColumn(String childTable, SqlColumnMeta valueColumn) {
        return "Mapped to column "
                + quote(valueColumn.getName())
                + " of child table "
                + quote(childTable)
                + nativeType(valueColumn);
    }

    /** Attribute level: a reference attribute linked through a junction table. */
    public static String junctionReference(String junctionTable, String targetTable) {
        return "Mapped to junction table "
                + quote(junctionTable)
                + " (references table "
                + quote(targetTable)
                + ')';
    }

    /**
     * Attribute level: a composite-key UID — the main key column plus the additional PK columns
     * of the given table.
     */
    public static String compositeKey(String tableName, SqlColumnMeta mainColumn, List<SqlColumnMeta> additionalColumns) {
        var columns = new StringBuilder(quote(mainColumn.getName()));
        for (var additional : additionalColumns) {
            columns.append(" + ").append(quote(additional.getName()));
        }
        return "Mapped to columns "
                + columns
                + " of table "
                + quote(tableName)
                + " (composite key)"
                + nativeType(mainColumn);
    }

    private static String tableKind(SqlTableInfo table) {
        var type = table.getTableType();
        return type != null && "VIEW".equalsIgnoreCase(type) ? "view" : "table";
    }

    private static String nativeType(SqlColumnMeta column) {
        var type = column.getTypeName();
        return type != null && !type.isBlank() ? " (native type: " + type + ")" : "";
    }

    private static String quote(String value) {
        return '"' + value + '"';
    }
}
