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
public class SqlUpdateOperation extends SqlWriteOperationSupport implements ObjectUpdateOperation {

    public SqlUpdateOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        super(context, objectClass);
    }

    @Override
    public Set<AttributeDelta> updateDelta(
            Uid uid, Set<AttributeDelta> modifications, OperationOptions options) {
        requireWritable();
        var requested = modifications != null ? Set.copyOf(modifications) : Collections.<AttributeDelta>emptySet();
        if (requested.isEmpty()) {
            return requested;
        }

        return inTransaction("Update " + objectClass.name(), connection -> {
            var current = requireByUid(connection, uid, options, true);
            var table = tablePath();
            var columnValues = updateColumnValues(table, current, requested);
            if (columnValues.isEmpty()) {
                return requested;
            }

            var update = new SQLUpdateClause(
                    connection.getConnection(), context.getSqlTemplates(), table);
            applyColumnValues(update, columnValues);
            var affected = update.where(uidPredicate(table, uid)).execute();
            if (affected == 0) {
                throw new UnknownUidException(uid, objectClass.objectClass());
            }
            if (affected != 1) {
                throw new ConnectorException(
                        "Update affected " + affected + " rows instead of one");
            }
            return requested;
        });
    }
}
