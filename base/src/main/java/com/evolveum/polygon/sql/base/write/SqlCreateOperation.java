/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.types.Path;
import com.querydsl.sql.dml.SQLInsertClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;

import java.util.Set;

/** QueryDSL-based create operation for a writable SQL table. */
public class SqlCreateOperation implements ObjectCreateOperation {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;

    public SqlCreateOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = new SqlWriteOperationSupport(context, objectClass);
    }

    @Override
    public ConnectorObject create(Set<Attribute> createAttributes, OperationOptions options) {
        support.requireWritable();
        return support.inTransaction("Create " + objectClass.name(), connection -> {
            var table = support.tablePath();
            var uidDefinition = support.uidDefinition();
            var suppliedUid = support.suppliedUid(createAttributes);
            var columnValues = support.createColumnValues(table, createAttributes);
            var insert = new SQLInsertClause(
                    connection.getConnection(), context.getSqlTemplates(), table);
            support.applyColumnValues(insert, columnValues);

            final org.identityconnectors.framework.common.objects.Uid uid;
            if (suppliedUid != null) {
                var affected = insert.execute();
                if (affected != 1) {
                    throw new ConnectorException(
                            "Create affected " + affected + " rows instead of one");
                }
                uid = suppliedUid;
            } else {
                if (uidDefinition.connId().isCreateable()) {
                    throw support.invalid(
                            "Required attribute " + uidDefinition.connId().getName() + " is missing");
                }
                var generatedPath = generatedKeyPath(uidDefinition.sql(), table);
                uid = support.generatedUid(uidDefinition.sql(),
                        support.generatedKey(insert, generatedPath), table, columnValues);
            }

            var created = support.findByUid(connection, uid, options, false);
            if (created == null) {
                throw new ConnectorException("Created object " + uid + " could not be read back");
            }
            return created;
        });
    }

    private Path<?> generatedKeyPath(SqlAttributeMapping mapping, Path<?> table) {
        if (mapping instanceof SqlAttributeMapping.SingleColumn singleColumn) {
            return singleColumn.dslPath(table);
        }
        if (mapping instanceof SqlAttributeMapping.MultiColumn multiColumn) {
            return multiColumn.mainColumn().dslPath(table);
        }
        throw new ConnectorException("Unsupported UID mapping " + mapping.getClass().getName());
    }
}
