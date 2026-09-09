/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.sql.dml.SQLUpdateClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.Collection;

/** Updates primary-row attributes within the shared coordinator's transaction. */
final class SqlUpdateOperation implements UpdateOperationHandler {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;

    SqlUpdateOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass,
            SqlWriteOperationSupport support) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = support;
    }

    @Override
    public Capability<AttributeDelta, UpdateOperationHandler> canHandle(
            Collection<AttributeDelta> modifications, OperationOptions options) {
        return new Capability<>(this, modifications.stream()
                .filter(delta -> !support.isRelatedAttribute(delta.getName())).toList());
    }

    @Override
    public boolean requiresOriginalState() {
        return false;
    }

    @Override
    public void update(UpdateRequest request, OperationOptions options, ContextLookup operationContext) {
        var connection = operationContext.get(SqlWriteContext.class).connection();
        var current = support.requireByUid(connection, request.uid(), true);
        var table = support.tablePath();
        var columnValues = support.updateColumnValues(table, current, request.attributeDeltaSet());
        if (!columnValues.isEmpty()) {
            var update = new SQLUpdateClause(
                    connection.getConnection(), context.getSqlTemplates(), table);
            support.applyColumnValues(update, columnValues);
            var affected = update.where(support.uidPredicate(table, request.uid())).execute();
            if (affected == 0) {
                throw new UnknownUidException(request.uid(), objectClass.objectClass());
            }
            if (affected != 1) {
                throw new ConnectorException(
                        "Update affected " + affected + " rows instead of one");
            }
        }
    }
}
