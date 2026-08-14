/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.sql.dml.SQLDeleteClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

/** QueryDSL-based delete operation for a writable SQL table. */
public class SqlDeleteOperation implements ObjectDeleteOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;

    public SqlDeleteOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = new SqlWriteOperationSupport(context, objectClass);
    }

    @Override
    public void delete(Uid uid, OperationOptions options) {
        support.requireWritable();
        support.inTransaction("Delete " + objectClass.name(), connection -> {
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
            return null;
        });
    }
}
