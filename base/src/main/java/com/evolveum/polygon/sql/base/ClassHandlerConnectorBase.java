/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.spi.ObjectClassHandler;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.operations.SearchOp;

/**
 * Base connector with support for separate handlers per object class.
 * Allows mixing and matching different operation handlers for different object classes.
 *
 * <p>All methods are called by the ConnId framework and may be invoked concurrently.
 * Implementations must ensure thread-safety for all shared state.</p>
 */
public abstract class ClassHandlerConnectorBase implements Connector, SearchOp<Filter> {

    public abstract SqlBaseContext context();

    public abstract ObjectClassHandler handlerFor(ObjectClass objectClass) throws UnsupportedOperationException;
}