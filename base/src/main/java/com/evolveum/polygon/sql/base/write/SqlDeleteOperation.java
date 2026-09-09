/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.sql.dml.SQLDeleteClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

/** Deletes the primary row after the shared coordinator has run related-row cleanup. */
final class SqlDeleteOperation implements DeleteOperationHandler {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;

    SqlDeleteOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass,
            SqlWriteOperationSupport support) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = support;
    }

    @Override
    public void delete(Uid uid, OperationOptions options, ContextLookup operationContext) {
        var connection = operationContext.get(SqlWriteContext.class).connection();
        var table = support.tablePath();
        var delete = new SQLDeleteClause(
                connection.getConnection(), context.getSqlTemplates(), table);
        var affected = delete.where(support.uidPredicate(table, uid)).execute();
        if (affected == 0) {
            throw new UnknownUidException(uid, objectClass.objectClass());
        }
        if (affected != 1) {
            throw new ConnectorException(
                    "Delete affected " + affected + " rows instead of one");
        }
    }
}
