/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import org.identityconnectors.framework.common.objects.ObjectClass;

/**
 * Contract for handling operations and object classes in a specific object type.
 */
public interface ObjectClassHandler {

    /**
     * Checks if specific SPI operation is supported for specific object type.
     * @param operationType SPI Operation interface
     * @return Operation handler
     * @throws UnsupportedOperationException If the SPI Operation is not supported
     */
    <T extends ObjectClassOperation> T checkSupported(Class<T> operationType) throws UnsupportedOperationException;

    ObjectClass objectClass();
}