/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlSchema;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads Groovy schema scripts into {@link SqlSchemaBuilder}.
 * Uses a persistent GroovyShell across all script evaluations.
 *
 * <p>Groovy scripts use the unified build DSL:
 * <pre>{@code
 * objectClass("User") {
 *     sql { table "app_user" }
 *     attribute("id") {
 *         connId { name UID }
 *         sql { primaryKey(); autoIncrement(); type INT }
 *     }
 * }
 * }</pre>
 *
 * <p>The {@code objectClass} top-level function is bound via {@link SchemaMethod}.
 */
public class SqlGroovySchemaLoader {

    private final SqlBaseContext context;
    private final SqlSchemaBuilderImpl builder;
    private final GroovyShell shell;

    public SqlGroovySchemaLoader(SqlBaseContext context, SqlSchemaBuilderImpl builder, GroovyContext groovyContext) {
        this.context = context;
        this.builder = builder;
        this.shell = groovyContext.createShell();
        shell.setVariable("objectClass", new SchemaMethod(builder));
        shell.setVariable("context", context);
    }

    /**
     * Loads a Groovy script from a file system path.
     *
     * @param scriptPath path to the Groovy script file
     * @throws RuntimeException if loading fails
     */
    public void load(Path scriptPath) {
        if (!Files.exists(scriptPath)) {
            return;
        }
        try {
            var script = shell.parse(scriptPath.toFile());
            script.run();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Groovy script: " + e.getMessage(), e);
        }
    }

    /**
     * Loads a Groovy script from a classpath resource.
     *
     * @param resourceName classpath resource path (e.g., "/schema.groovy")
     * @throws RuntimeException if loading fails
     */
    public void loadResource(String resourceName) {
        try (var is = this.getClass().getResourceAsStream(resourceName)) {
            if (is == null) {
                return;
            }
            shell.evaluate(new InputStreamReader(is), resourceName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Groovy script from resource: " + resourceName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Alias for loadResource to match the manifest-based API convention.
     */
    public void loadFromResource(String resourceName) {
        loadResource(resourceName);
    }

    /**
     * Loads a Groovy script from a text string.
     *
     * @param scriptText the Groovy script text
     * @throws RuntimeException if loading fails
     */
    public void load(String scriptText) {
        shell.evaluate(scriptText);
    }

    /**
     * Builds the schema from the populated builder and sets it on the context.
     *
     * @return the built SqlSchema
     */
    public SqlSchema buildAndSet() {
        var schema = builder.build();
        context.schema(schema);
        return schema;
    }

    /**
     * Wrapper that provides the {@code objectClass} function for Groovy scripts.
     * Uses GroovyClosures.callAndReturnDelegate for proper closure delegation.
     */
    private static class SchemaMethod {
        private final SqlSchemaBuilderImpl builder;

        SchemaMethod(SqlSchemaBuilderImpl builder) {
            this.builder = builder;
        }

        /**
         * Handles both {@code objectClass("name")} and {@code objectClass("name") { ... }}.
         */
        @SuppressWarnings("unchecked")
        public Object call(Object... args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("objectClass() requires a name argument");
            }
            String name = args[0] instanceof String s ? s : args[0].toString();
            var oc = builder.objectClass(name);

            if (args.length > 1 && args[1] instanceof Closure<?> closure) {
                return GroovyClosures.callAndReturnDelegate(closure, oc);
            }
            return oc;
        }
    }
}