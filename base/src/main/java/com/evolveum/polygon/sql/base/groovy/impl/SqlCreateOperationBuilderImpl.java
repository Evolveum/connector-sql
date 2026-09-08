/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.AbstractCreateOperationBuilder;
import com.evolveum.polygon.conndev.spi.AttributeCreateOperationHandler;
import com.evolveum.polygon.conndev.spi.CreateOperationHandler;
import com.evolveum.polygon.conndev.spi.OperationExecutor;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.write.SqlWriteHandlers;

import java.util.Collection;
import java.util.List;

public final class SqlCreateOperationBuilderImpl extends AbstractCreateOperationBuilder<SqlObjectClassDefinition> {

    private final SqlWriteHandlers writes;

    SqlCreateOperationBuilderImpl(SqlObjectOperationBuilderImpl parent, SqlWriteHandlers writes) {
        super(parent);
        this.writes = writes;
    }

    @Override
    protected OperationExecutor operationExecutor() {
        return writes.executor("Create");
    }

    @Override
    protected Collection<CreateOperationHandler> collectHandlers() {
        return List.of(writes.createHandler());
    }

    @Override
    protected Collection<AttributeCreateOperationHandler> attributeHandlers() {
        return writes.attributeCreateHandlers();
    }
}
