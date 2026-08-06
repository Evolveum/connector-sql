/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlOperationSupportBuilder;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;

import java.io.InputStreamReader;

/**
 * Builds operation handlers for SQL connector.
 * Provides hooks for registering operation handlers programmatically or via Groovy scripts.
 */
public class SqlHandlerLoader {

    private final GroovyShell shell;

    public SqlHandlerLoader(SqlBaseContext context, SqlOperationSupportBuilder builder) {
        var groovyContext = context.configuration().groovyContext();
        this.shell = groovyContext.createShell();
        shell.setVariable("objectClass", new MethodClosure(builder, "objectClass"));
    }

    /**
     * Evaluates a Groovy script from a classpath resource as handler definitions.
     * Scripts can call objectClass("name") { search(...) } to register handlers.
     */
    public void loadFromResource(String resourceName) {
        try (var is = this.getClass().getClassLoader().getResourceAsStream(resourceName)) {
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

}