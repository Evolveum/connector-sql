/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.yaml.model;

/**
 * The {@code sql:} top-level block of a declarative YAML schema document — the counterpart of the
 * Groovy {@code sql { table "..." } } DSL. Used by auto-discovery to correlate this object class
 * with a JDBC table.
 */
public class YamlSqlBlock {

    public String table;
    public String schema;
}
