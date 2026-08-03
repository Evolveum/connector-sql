/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

/**
 * Lightweight orchestrator for table/view discovery filtering.
 *
 * <p>Holds the scan-type flags and delegates include/exclude logic
 * to two separate {@link NameFilter} instances — one for tables, one for views.
 */
public final class TableFilter {

    private final boolean scanTables;
    private final boolean scanViews;
    private final NameFilter tableFilter;
    private final NameFilter viewFilter;

    /**
     * Creates a TableFilter from raw config values.
     *
     * @param scanTables whether tables should be scanned
     * @param scanViews  whether views should be scanned
     * @param tableInclude regex for including tables (optional)
     * @param viewInclude  regex for including views (optional)
     * @param tableExclude regex for excluding tables (optional)
     * @param viewExclude  regex for excluding views (optional)
     */
    public TableFilter(boolean scanTables, boolean scanViews,
                       String tableInclude, String viewInclude,
                       String tableExclude, String viewExclude) {
        this.scanTables = scanTables;
        this.scanViews = scanViews;
        this.tableFilter = new NameFilter(tableInclude, tableExclude);
        this.viewFilter = new NameFilter(viewInclude, viewExclude);
    }

    /**
     * Returns true if schema discovery is enabled at all.
     */
    public boolean isDiscoveryEnabled() {
        return scanTables || scanViews;
    }

    /**
     * Checks whether a discovered table view from JDBC metadata should be processed.
     *
     * @param tableType JDBC table type (e.g. "TABLE", "BASE TABLE", "VIEW")
     * @param tableName the table name
     * @return true if the table should be scanned for columns/keys
     */
    public boolean passes(String tableType, String tableName) {
        // 1. Type check: respect scanTables / scanViews flags
        if (isTable(tableType) && !scanTables) {
            return false;
        }
        if (isView(tableType) && !scanViews) {
            return false;
        }
        // Not a table or view type
        if (!isTable(tableType) && !isView(tableType)) {
            return false;
        }

        // 2. Delegate to the appropriate NameFilter
        if (isTable(tableType)) {
            return tableFilter.passes(tableName);
        }
        // isView(tableType) at this point
        return viewFilter.passes(tableName);
    }

    private static boolean isTable(String tableType) {
        return "TABLE".equalsIgnoreCase(tableType) || "BASE TABLE".equalsIgnoreCase(tableType);
    }

    private static boolean isView(String tableType) {
        return "VIEW".equalsIgnoreCase(tableType);
    }
}