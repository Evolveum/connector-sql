/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.Map;

/**
 * ObjectClassHandler implementation for SQL connector.
 */
public class SqlObjectClassHandler implements ObjectClassHandler {

    private final ObjectClass objectClass;
    private final Map<Class<?>, Object> handlers;

    public SqlObjectClassHandler(ObjectClass objectClass, Map<Class<?>, Object> handlers) {
        this.objectClass = objectClass;
        this.handlers = handlers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends ObjectClassOperation> T checkSupported(Class<T> operationType) throws UnsupportedOperationException {
        Object handler = handlers.get(operationType);
        if (handler == null) {
            throw new UnsupportedOperationException("Operation %s is not supported for %s"
                .formatted(operationType.getName(), objectClass));
        }
        return (T) handler;
    }

    @Override
    public ObjectClass objectClass() {
        return objectClass;
    }
}