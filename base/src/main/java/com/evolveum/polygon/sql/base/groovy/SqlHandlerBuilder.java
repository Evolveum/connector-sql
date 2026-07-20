/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.spi.*;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.sync.SqlSyncOperation;
import com.evolveum.polygon.sql.base.sync.SyncConfig;
import com.evolveum.polygon.sql.base.sync.strategy.SoftDeleteFilterStrategy;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds operation handlers for SQL connector.
 * Provides hooks for registering operation handlers programmatically or via Groovy scripts.
 */
public class SqlHandlerBuilder {

    private final SqlBaseContext context;
    private final Map<ObjectClass, Map<Class<?>, Object>> handlers = new HashMap<>();
    private final GroovyShell shell;

    public SqlHandlerBuilder(SqlBaseContext context) {
        this.context = context;
        var groovyContext = context.configuration().groovyContext();
        this.shell = groovyContext.createShell();
        shell.setVariable("objectClass", new HandlerMethod(this));
    }

    /** Registers an operation handler for an object class (e.g., an {@code ObjectSearchOperation}). */
    public SqlHandlerBuilder register(ObjectClass objectClass, Class<?> operationType, Object handler) {
        handlers.computeIfAbsent(objectClass, k -> new HashMap<>()).put(operationType, handler);
        return this;
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

    /**
     * Programmatically registers a handler for an operation type.
     *
     * @param operationType type of operation (e.g., ObjectSearchOperation.class)
     * @param handler       the handler instance
     */
    public SqlHandlerBuilder create(Class<?> operationType, Object handler) {
        return this;
    }

    /**
     * Builds the handler map for lookup by object class.
     */
    public Map<ObjectClass, ObjectClassHandler> build() {
        Map<ObjectClass, ObjectClassHandler> result = new HashMap<>();
        for (Map.Entry<ObjectClass, Map<Class<?>, Object>> entry : handlers.entrySet()) {
            result.put(entry.getKey(), new SqlObjectClassHandler(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Checks if a handler for the given operation type has already been registered.
     */
    public boolean hasRegistered(ObjectClass objectClass, Class<?> operationType) {
        var map = handlers.get(objectClass);
        return map != null && map.containsKey(operationType);
    }

    /**
     * Wrapper that provides the {@code objectClass} function for Groovy handler scripts.
     */
    private static class HandlerMethod {
        private final SqlHandlerBuilder builder;

        HandlerMethod(SqlHandlerBuilder builder) {
            this.builder = builder;
        }

        @SuppressWarnings("unchecked")
        public Object call(Object... args) {
            if (args.length == 0) {
                throw new IllegalArgumentException("objectClass() requires a name argument");
            }
            String name = args[0] instanceof String s ? s : args[0].toString();
            var oc = new ObjectClass(name);

            Map<Class<?>, Object> map = builder.handlers.computeIfAbsent(oc, k -> new HashMap<>());

            if (args.length > 1 && args[1] instanceof Closure<?> closure) {
                var facade = new GroovyHandlerFacade(map, oc, builder.context);
                return GroovyClosures.callAndReturnDelegate(closure, facade);
            }
            return new GroovyHandlerFacade(map, oc, builder.context);
        }
    }

    /**
     * Facade for Groovy operation scripts.
     * Provides operation registration methods: search, create, update, delete, sync.
     */
    public static class GroovyHandlerFacade {
        private final Map<Class<?>, Object> handlers;
        private final ObjectClass objectClass;
        private final SqlBaseContext context;

        GroovyHandlerFacade(Map<Class<?>, Object> handlers, ObjectClass objectClass, SqlBaseContext context) {
            this.handlers = handlers;
            this.objectClass = objectClass;
            this.context = context;
        }

        public GroovyHandlerFacade search(Object handler) {
            handlers.put(ObjectSearchOperation.class, handler);
            return this;
        }

        public GroovyHandlerFacade create(Object handler) {
            handlers.put(ObjectCreateOperation.class, handler);
            return this;
        }

        public GroovyHandlerFacade update(Object handler) {
            handlers.put(ObjectUpdateOperation.class, handler);
            return this;
        }

        public GroovyHandlerFacade delete(Object handler) {
            handlers.put(ObjectDeleteOperation.class, handler);
            return this;
        }

        /**
         * Configures sync operation for this object class with default settings.
         *
         * <pre>
         * objectClass("User") {
         *     sync {}   // use default SyncConfig
         * }
         * </pre>
         */
        public GroovyHandlerFacade sync() {
            var def = context.findSqlObjectClass(objectClass);
            if (def != null) {
                handlers.put(ObjectSyncOperation.class,
                    new SqlSyncOperation(context, def, SyncConfig.defaultFor(def)));
            }
            return this;
        }

        /**
         * Configures sync operation with custom settings.
         *
         * <pre>
         * objectClass("User") {
         *     sync {
         *         syncColumn "updated_at"
         *         softDeleteColumn "deleted_at"
         *         pageSize 500
         *     }
         * }
         * </pre>
         */
        @SuppressWarnings("unchecked")
        public GroovyHandlerFacade sync(Map<String, Object> config) {
            SyncConfig baseConfig = SyncConfig.defaultFor(context.findSqlObjectClass(objectClass));

            if (config.containsKey("syncColumn")) {
                var val = config.get("syncColumn");
                String colName = val instanceof String s ? s : val.toString();
                baseConfig = baseConfig.withExplicitColumn(colName);
            }

            if (config.containsKey("softDeleteColumn")) {
                var val = config.get("softDeleteColumn");
                String colName = val instanceof String s ? s : val.toString();
                baseConfig = baseConfig.withFilterStrategy(new SoftDeleteFilterStrategy(colName));
            }

            if (config.containsKey("pageSize") && config.get("pageSize") instanceof Number n) {
                baseConfig = baseConfig.withPageSize(n.intValue());
            }

            var def = context.findSqlObjectClass(objectClass);
            if (def != null && config.containsKey("syncColumn")) {
                handlers.put(ObjectSyncOperation.class,
                    new SqlSyncOperation(context, def, baseConfig));
            } else if (def != null && !config.isEmpty()) {
                // Only register if we have meaningful config or default was not already registered
                handlers.put(ObjectSyncOperation.class,
                    new SqlSyncOperation(context, def, baseConfig));
            }
            return this;
        }
    }
}