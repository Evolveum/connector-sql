/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds operation handlers for SQL connector.
 * Parses Groovy operation scripts and generates handlers.
 */
public class SqlHandlerBuilder {

    private final SqlBaseContext context;
    private final Map<ObjectClass, Map<Class<?>, Object>> handlers = new HashMap<>();

    public SqlHandlerBuilder(SqlBaseContext context) {
        this.context = context;
    }

    public SqlHandlerBuilder create(Class<?> operationType, Object handler) {

        return this;
    }

    public Map<ObjectClass, ObjectClassHandler> build() {
        Map<ObjectClass, ObjectClassHandler> result = new HashMap<>();
        for (Map.Entry<ObjectClass, Map<Class<?>, Object>> entry : handlers.entrySet()) {
            result.put(entry.getKey(), new SqlObjectClassHandler(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}