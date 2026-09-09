/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.spi.ObjectClassOperation;
import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlOperationSupportBuilder;
import com.evolveum.polygon.sql.base.yaml.YamlSqlOperationsLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Builds operation handlers for SQL connector.
 * Provides hooks for registering operation handlers programmatically or via Groovy scripts.
 */
public class SqlHandlerLoader {

    private final SqlBaseContext context;
    private final GroovyShell shell;
    private final SqlOperationSupportBuilder builder;
    private final YamlSqlOperationsLoader operationsLoader;

    public SqlHandlerLoader(SqlBaseContext context, SqlOperationSupportBuilder builder) {
        this.context = context;
        this.builder = builder;
        var groovyContext = context.configuration().groovyContext();
        this.shell = groovyContext.createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
        this.operationsLoader = new YamlSqlOperationsLoader(builder, new GroovyScriptCompiler(groovyContext));
    }

    /**
     * Loads operation handler definitions from a classpath resource. Groovy scripts
     * ({@code objectClass("name") { search(...) }}) are evaluated on the Groovy shell; YAML
     * documents ({@code .yaml}/{@code .yml}) are driven through the location-aware engine onto the
     * same live {@link SqlOperationSupportBuilder}.
     */
    public void loadFromResource(String resourceName) {
        if (ScriptResources.isYaml(resourceName)) {
            loadYamlFromResource(resourceName);
        } else {
            loadGroovyFromResource(resourceName);
        }
    }

    private void loadGroovyFromResource(String resourceName) {
        try (var is = this.getClass().getResourceAsStream(resourceName)) {
            if (is == null) return;
            shell.evaluate(new InputStreamReader(is), resourceName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Groovy handler script from resource: " + resourceName + ": " + e.getMessage(), e);
        }
    }

    private void loadYamlFromResource(String resource) {
        try (var is = this.getClass().getResourceAsStream(resource)) {
            if (is == null) return;
            operationsLoader.load(new InputStreamReader(is), resource);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load YAML operations document from resource: " + resource + ": " + e.getMessage(), e);
        }
    }

    /**
     * Evaluates a Groovy script from a text string.
     *
     * @param scriptText the Groovy script text
     */
    public void loadFromString(String scriptText) {
        shell.evaluate(scriptText);
    }

    public Script parse(String scriptText) {
        return shell.parse(scriptText);
    }

    /** Registers a complete custom operation before built-in handlers are built. */
    public <T extends ObjectClassOperation> void register(
            ObjectClass objectClass, Class<T> operationType, T operation) {
        var definition = context.findSqlObjectClass(objectClass);
        var className = definition != null
                ? definition.name()
                : objectClass.getObjectClassValue();
        var objectBuilder = builder.objectClass(className);
        objectBuilder.register(operationType, operation);
    }

}
