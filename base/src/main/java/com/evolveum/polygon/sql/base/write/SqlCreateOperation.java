/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.spi.CreateOperationHandler;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.types.Path;
import com.querydsl.sql.dml.SQLInsertClause;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.Set;

/** Creates the primary SQL row within the shared coordinator's transaction. */
final class SqlCreateOperation implements CreateOperationHandler {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;

    SqlCreateOperation(SqlBaseContext context, SqlObjectClassDefinition objectClass,
            SqlWriteOperationSupport support) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = support;
    }

    @Override
    public Capability<Attribute, CreateOperationHandler> canHandle(
            Collection<Attribute> attributes, OperationOptions options) {
        return new Capability<>(this, attributes.stream()
                .filter(attribute -> !support.isRelatedAttribute(attribute.getName())).toList());
    }

    @Override
    public Result create(Set<Attribute> createAttributes, OperationOptions options, ContextLookup operationContext) {
        var connection = operationContext.get(SqlWriteContext.class).connection();
        var table = support.tablePath();
        var uidDefinition = support.uidDefinition();
        var suppliedUid = support.suppliedUid(createAttributes);
        var columnValues = support.createColumnValues(table, createAttributes);
        var insert = new SQLInsertClause(
                connection.getConnection(), context.getSqlTemplates(), table);
        support.applyColumnValues(insert, columnValues);

        final Uid uid;
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
                    support.generatedKey(insert, table, generatedPath), table, columnValues);
        }
        // JDBC may normalize a supplied key (e.g. decimal 2 becomes 2.00).
        // Give the coordinator the canonical UID before it creates child attributes.
        var created = support.requireByUid(connection, uid, false);
        return new Result(objectClass.objectClass(), created.getUid(), created);
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
