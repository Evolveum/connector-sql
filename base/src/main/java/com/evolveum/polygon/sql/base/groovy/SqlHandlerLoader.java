/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlOperationSupportBuilder;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.io.InputStreamReader;

/**
 * Builds operation handlers for SQL connector.
 * Provides hooks for registering operation handlers programmatically or via Groovy scripts.
 */
public class SqlHandlerLoader {

    private final SqlBaseContext context;
    private final GroovyShell shell;
    private final SqlOperationSupportBuilder builder;

    public SqlHandlerLoader(SqlBaseContext context, SqlOperationSupportBuilder builder) {
        this.context = context;
        this.builder = builder;
        var groovyContext = context.configuration().groovyContext();
        this.shell = groovyContext.createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
    }

    /**
     * Evaluates a Groovy script from a classpath resource as handler definitions.
     * Scripts can call objectClass("name") { search(...) } to register handlers.
     */
    public void loadFromResource(String resourceName) {
        try (var is = this.getClass().getResourceAsStream(resourceName)) {
            if (is == null) return;
            shell.evaluate(new InputStreamReader(is), resourceName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Groovy handler script from resource: " + resourceName + ": " + e.getMessage(), e);
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

    public groovy.lang.Script parse(String scriptText) {
        return shell.parse(scriptText);
    }

    /** Registers a complete custom operation before built-in handlers are built. */
    public <T extends ObjectClassOperation> void register(
            ObjectClass objectClass, Class<T> operationType, T operation) {
        var definition = context.findSqlObjectClass(objectClass);
        var className = definition != null
                ? definition.name()
                : objectClass.getObjectClassValue();
        var objectBuilder = (SqlObjectOperationBuilderImpl)
                builder.objectClass(className);
        objectBuilder.register(operationType, operation);
    }

}
