/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.fail;

/**
 * Abstract base class for SQL connector integration tests.
 * <p>
 * Eliminates boilerplate by providing:</p>
 * <ul>
 *   <li>H2 in-memory database with unique URL per test class</li>
 *   <li>Connector initialization / disposal lifecycle</li>
 *   <li>Common helpers: search(), getAttr(), schemaNames(), findOC()</li>
 *   <li>Schema + data loading from SQL strings or resource paths</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @Test
 * public class MyIntegrationTest extends SqlIntegrationTestBase {
 *
 *     private TestConnector connector;
 *
 *     @Override
 *     protected String schemaSql() { return "CREATE TABLE ..."; }
 *
 *     @Override
 *     protected String dataSql() { return "INSERT INTO ..."; }
 *
 *     @Test
 *     public void testSomething() throws Exception {
 *         connector.init(defaultConfig());
 *         List<ConnectorObject> results = search("my_table", null);
 *         assertThat(results).hasSize(1);
 *     }
 * }
 * }</pre>
 *
 * @param <C> connector type
 */
public abstract class SqlIntegrationTestBase<C extends AbstractGroovySqlConnector<SqlConnectorConfiguration>> {

    private static final int ID = (int) (ThreadLocalRandom.current().nextDouble() * Integer.MAX_VALUE);
    protected final String url = "jdbc:h2:mem:sqlitest" + ID + ";DB_CLOSE_DELAY=-1;MODE=MySQL";
    protected SqlConnectorConfiguration defaultConfiguration;

    protected C connector;

    /**
     * Minimal test connector with no custom handlers.
     * Subclasses should override this class if they need custom initializeObjectClassHandler() behavior.
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
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {}
    }

    @BeforeMethod
    public void setUp() throws Exception {
        var schema = schemaSql();
        var data = dataSql();

        if (schema != null || data != null) {
            try (Connection conn = DriverManager.getConnection(url, "sa", "");
                 var stmt = conn.createStatement()) {
                if (schema != null) stmt.execute(schema);
                if (data != null) stmt.execute(data);
                stmt.execute("COMMIT");
            }
        }

        var resourcePaths = resourceSchemaPaths();
        if (resourcePaths != null && resourcePaths.length > 0) {
            try (Connection conn = DriverManager.getConnection(url, "sa", "");
                 var stmt = conn.createStatement()) {
                for (String path : resourcePaths) {
                    stmt.execute(readResource(path));
                }
                stmt.execute("COMMIT");
            }
        }

        this.defaultConfiguration = buildConfiguration();
        initConnector();
    }


    @AfterMethod
    public void tearDown() {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
    }

    protected void initConnector() {

    }


    // ─── Hooks (override in subclasses) ───

    /**
     * SQL DDL for creating schema. Return null if not needed.
     */
    protected String schemaSql() {
        return null;
    }

    /**
     * SQL DML for inserting test data. Return null if not needed.
     */
    protected String dataSql() {
        return null;
    }

    /**
     * Classpath resource paths for schema + data. Each file will be executed sequentially.
     * Return null if not needed.
     */
    protected String[] resourceSchemaPaths() {
        return null;
    }

    /**
     * Groovy handler script. Return null if not needed.
     */
    protected String groovyScript() {
        return null;
    }

    /**
     * Build the default connector configuration. Override to customize.
     */
    protected SqlConnectorConfiguration buildConfiguration() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(true);
        config.setDevelopmentMode(true);
        return config;
    }

    // ─── Config builders ───

    /**
     * Default configuration with scan enabled.
     */
    public SqlConnectorConfiguration defaultConfig() {
        return defaultConfiguration;
    }

    /**
     * Configuration with development mode enabled.
     */
    public SqlConnectorConfiguration devConfig() {
        var config = copyConfig(defaultConfiguration);
        config.setDevelopmentMode(true);
        return config;
    }

    /**
     * Configuration with scanning disabled (for Groovy/YAML-defined schemas).
     */
    public SqlConnectorConfiguration noScanConfig() {
        var config = copyConfig(defaultConfiguration);
        config.setScanTables(false);
        config.setScanViews(false);
        return config;
    }

    private SqlConnectorConfiguration copyConfig(SqlConnectorConfiguration src) {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(src.getJdbcUrl());
        config.setUsername(src.getUsername());
        var accessor = new GuardedStringAccessor();
        src.getPassword().access(accessor);
        config.setPassword(new GuardedString(accessor.getClearChars()));
        config.setPoolSize(src.getPoolSize());
        config.setConnectionTimeout(src.getConnectionTimeout());
        config.setIdleTimeout(src.getIdleTimeout());
        config.setValidateConnectionOnBorrow(src.getValidateConnectionOnBorrow());
        config.setScanTables(src.getScanTables());
        config.setScanViews(src.getScanViews());
        config.setDevelopmentMode(src.getDevelopmentMode());
        config.setPgDumpPath(src.getPgDumpPath());
        return config;
    }

    // ─── Helpers ───

    /**
     * Execute a search query and collect all results.
     */
    protected List<ConnectorObject> search(String objectClass, Filter filter) throws Exception {
        return search(new ObjectClass(objectClass), filter);
    }

    /**
     * Execute a search query and collect all results.
     */
    protected List<ConnectorObject> search(ObjectClass objectClass, Filter filter) throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(objectClass, filter, results::add, opts());
        return results;
    }

    /**
     * Get a single attribute value from a connector object.
     */
    protected Object getAttr(ConnectorObject obj, String attrName) {
        var attr = obj.getAttributeByName(attrName);
        return (attr != null && !attr.getValue().isEmpty())
                ? attr.getValue().getFirst() : null;
    }

    /**
     * Get all object class names from schema (lowercase).
     */
    protected List<String> schemaNames() {
        return connector.schema().getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    /**
     * Find an ObjectClassInfo by approximate name matching.
     */
    protected ObjectClassInfo findOC(String name) {
        for (var oc : connector.schema().getObjectClassInfo()) {
            if (oc.getType().equalsIgnoreCase(name)) {
                return oc;
            }
        }
        return null;
    }

    /**
     * Get a map of attribute info for a given object class.
     */
    protected Map<String, AttributeInfo> attrsOf(String objectClass) {
        var oc = findOC(objectClass);
        if (oc == null) {
            fail("Object class not found: " + objectClass);
        }
        return oc.getAttributeInfo().stream()
                .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
    }

    /**
     * Default empty operation options.
     */
    protected OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    /**
     * Read a classpath resource as a string.
     */
    protected String readResource(String path) {
        try {
            var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
            return new String(is.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }
}
