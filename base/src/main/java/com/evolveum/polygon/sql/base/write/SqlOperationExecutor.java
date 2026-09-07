/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.conndev.spi.OperationExecutor;
import com.evolveum.polygon.conndev.spi.OperationTransaction;
import com.evolveum.polygon.sql.base.SqlBaseContext;

import java.util.Collection;
import java.util.List;

/** Adapts a JDBC transaction to conndev's shared write lifecycle. */
final class SqlOperationExecutor implements OperationExecutor {

    private final SqlBaseContext context;
    private final SqlWriteOperationSupport support;
    private final String action;
    private final Collection<String> parentColumns;
    private final OperationExecutor delegate = OperationExecutor.transactional(this::open);

    SqlOperationExecutor(SqlBaseContext context, SqlWriteOperationSupport support,
            String action, Collection<String> parentColumns) {
        this.context = context;
        this.support = support;
        this.action = action;
        this.parentColumns = List.copyOf(parentColumns);
    }

    @Override
    public <T> T execute(Work<T> work) {
        support.requireWritable();
        try {
            return delegate.execute(work);
        } catch (RuntimeException failure) {
            throw support.translate(action, failure);
        }
    }

    private OperationTransaction open() throws Exception {
        var connection = context.getConnection();
        try {
            connection.setAutoCommit(false);
        } catch (Exception | Error failure) {
            connection.close();
            throw failure;
        }
        var operationContext = new SqlWriteContext(connection, support, parentColumns);
        return new OperationTransaction() {
            @Override
            public <T extends RetrievableContext> T get(Class<T> type) {
                return type.isInstance(operationContext)
                        ? type.cast(operationContext) : context.get(type);
            }

            @Override
            public void commit() throws Exception {
                connection.commit();
            }

            @Override
            public void rollback() throws Exception {
                connection.rollback();
            }

            @Override
            public void close() {
                connection.close();
            }
        };
    }
}
