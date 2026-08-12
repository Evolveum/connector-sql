/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;

import java.util.function.Consumer;

/**
 * Factory methods for creating test connector instances.
 * <p>
 * Eliminates the anonymous inner class boilerplate that every integration
 * test needs to extend {@link AbstractGroovySqlConnector}.
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Simple connector with no Groovy scripts
 * connector = TestConnectors.of();
 *
 * // Connector with a Groovy handler script
 * connector = TestConnectors.of(script -> builder -> builder.loadFromString(script));
 *
 * // Connector with a Groovy script and custom schema loading
 * connector = TestConnectors.of(
 *     script -> builder -> builder.loadFromString(script),
 *     loader -> loader.loadFromResource("/my/schema.groovy")
 * );
 * }</pre>
 */
public final class TestConnectors {

    private TestConnectors() {}

    /**
     * Create a default test connector with no custom handlers or schema scripts.
     */
    public static DefaultTestConnector of() {
        return new DefaultTestConnector();
    }

    /**
     * Create a test connector with a custom Groovy handler script.
     *
     * @param script the Groovy DSL script text
     * @return configured test connector
     */
    public static DefaultTestConnector of(String script) {
        return new DefaultTestConnector(script);
    }

    /**
     * Create a test connector with custom handler and schema initialization.
     *
     * @param handlerInitializer custom handler initialization
     * @param schemaInitializer custom schema initialization
     * @return configured test connector
     */
    public static DefaultTestConnector of(
            Consumer<SqlHandlerLoader> handlerInitializer,
            Consumer<SqlSchemaDefinitionLoader> schemaInitializer) {
        return new CustomTestConnector(handlerInitializer, schemaInitializer);
    }

    /**
     * Default test connector with no custom handlers.
     * Extends this class if you need custom behavior.
     */
    public static class DefaultTestConnector
            extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {

        private final String handlerScript;

        protected DefaultTestConnector() {
            super(false);
            this.handlerScript = null;
        }

        protected DefaultTestConnector(String handlerScript) {
            super(false);
            this.handlerScript = handlerScript;
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            if (handlerScript != null) {
                builder.loadFromString(handlerScript);
            }
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            // Schema is auto-discovered from DB tables
        }
    }

    /**
     * Test connector with custom handler and schema initializers.
     */
    public static class CustomTestConnector
            extends DefaultTestConnector {

        private final Consumer<SqlHandlerLoader> handlerInitializer;
        private final Consumer<SqlSchemaDefinitionLoader> schemaInitializer;

        protected CustomTestConnector(
                Consumer<SqlHandlerLoader> handlerInitializer,
                Consumer<SqlSchemaDefinitionLoader> schemaInitializer) {
            this.handlerInitializer = handlerInitializer;
            this.schemaInitializer = schemaInitializer;
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
            super.initializeObjectClassHandler(builder);
            if (handlerInitializer != null) {
                handlerInitializer.accept(builder);
            }
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
            if (schemaInitializer != null) {
                schemaInitializer.accept(loader);
            }
        }
    }
}
