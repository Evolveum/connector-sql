/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.AbstractUpdateOperationBuilder;
import com.evolveum.polygon.conndev.spi.OperationExecutor;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.write.SqlWriteHandlers;

import java.util.Collection;

public final class SqlUpdateOperationBuilderImpl extends AbstractUpdateOperationBuilder<SqlObjectClassDefinition> {

    private final SqlWriteHandlers writes;

    SqlUpdateOperationBuilderImpl(SqlObjectOperationBuilderImpl parent, SqlWriteHandlers writes) {
        super(parent);
        this.writes = writes;
    }

    @Override
    protected OperationExecutor operationExecutor() {
        return writes.executor("Update");
    }

    @Override
    protected Collection<UpdateOperationHandler> collectHandlers() {
        return writes.updateHandlers();
    }
}
