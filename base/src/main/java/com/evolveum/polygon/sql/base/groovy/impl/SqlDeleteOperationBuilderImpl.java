/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.AbstractDeleteOperationBuilder;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.conndev.spi.OperationExecutor;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.write.SqlWriteHandlers;

import java.util.Collection;
import java.util.List;

public final class SqlDeleteOperationBuilderImpl extends AbstractDeleteOperationBuilder<SqlObjectClassDefinition> {

    private final SqlWriteHandlers writes;

    SqlDeleteOperationBuilderImpl(SqlObjectOperationBuilderImpl parent, SqlWriteHandlers writes) {
        super(parent);
        this.writes = writes;
    }

    @Override
    protected OperationExecutor operationExecutor() {
        return writes.executor("Delete");
    }

    @Override
    protected Collection<DeleteOperationHandler> collectHandlers() {
        return List.of(writes.deleteHandler());
    }

    @Override
    protected Collection<DeleteOperationHandler> cleanupHandlers() {
        return writes.deleteCleanupHandlers();
    }
}
