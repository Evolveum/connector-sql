/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.sql.dml.SQLUpdateClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collections;
import java.util.Set;

/** QueryDSL-based update-delta operation for a writable SQL table. */
public class SqlUpdateOperation implements ObjectUpdateOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;

    public SqlUpdateOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = new SqlWriteOperationSupport(context, objectClass);
    }

    @Override
    public Set<AttributeDelta> updateDelta(
            Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        support.requireWritable();
        var requested = modifications != null ? Set.copyOf(modifications) : Collections.<AttributeDelta>emptySet();
        if (requested.isEmpty()) {
            return requested;
        }

        return support.inTransaction("Update " + objectClass.name(), connection -> {
            var current = support.requireByUid(connection, uid, true);
            var table = support.tablePath();
            var columnValues = support.updateColumnValues(table, current, requested);
            if (!columnValues.isEmpty()) {
                var update = new SQLUpdateClause(
                        connection.getConnection(), context.getSqlTemplates(), table);
                support.applyColumnValues(update, columnValues);
                var affected = update.where(support.uidPredicate(table, uid)).execute();
                if (affected == 0) {
                    throw new UnknownUidException(uid, objectClass.objectClass());
                }
                if (affected != 1) {
                    throw new ConnectorException(
                            "Update affected " + affected + " rows instead of one");
                }
            }
            support.updateRelatedRows(connection, uid, requested);
            return requested;
        });
    }
}
