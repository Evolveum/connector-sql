/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads Groovy schema and operation definitions.
 */
public class SqlSchemaLoader {

    private final SqlBaseContext context;

    public SqlSchemaLoader(SqlBaseContext context) {
        this.context = context;
    }

    public void load(Path schemaPath) {
        if (!Files.exists(schemaPath)) {
            return;
        }

        try {
            Binding binding = new Binding();
            binding.setVariable("context", context);

            GroovyShell shell = new GroovyShell(binding);
            Script script = shell.parse(schemaPath.toFile());
            script.run();

        } catch (Exception e) {
            throw new RuntimeException("Failed to load schema from " + schemaPath, e);
        }
    }
}