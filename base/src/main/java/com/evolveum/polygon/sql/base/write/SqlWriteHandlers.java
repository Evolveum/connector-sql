/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder.UpdateRequest;
import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.spi.AttributeCreateOperationHandler;
import com.evolveum.polygon.conndev.spi.CreateOperationHandler;
import com.evolveum.polygon.conndev.spi.DeleteOperationHandler;
import com.evolveum.polygon.conndev.spi.OperationExecutor;
import com.evolveum.polygon.conndev.spi.UpdateOperationHandler;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTableAccess;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.schema.SqlChildJoinConfig;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Registers SQL-specific steps for conndev's coordinators.
 * Routing, execution order, and transaction completion belong to conndev.
 */
public final class SqlWriteHandlers {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private final SqlWriteOperationSupport support;
    private final List<SqlChildJoinConfig> children;

    public SqlWriteHandlers(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
        this.support = new SqlWriteOperationSupport(context, objectClass);
        this.children = objectClass.relatedAttributeJoinConfigs();
    }

    public OperationExecutor executor(String operation) {
        var columns = new LinkedHashSet<String>();
        children.forEach(config -> config.joinKeys()
                .forEach(key -> columns.add(key.parentColumn())));
        objectClass.junctionJoinConfigs().forEach(config -> config.parentJoinKeys()
                .forEach(key -> columns.add(key.parentColumn())));
        return new SqlOperationExecutor(context, support, operation + " " + objectClass.name(), columns);
    }

    public CreateOperationHandler createHandler() {
        return new SqlCreateOperation(context, objectClass, support);
    }

    public Collection<AttributeCreateOperationHandler> attributeCreateHandlers() {
        return children.stream().map(this::createHandler).toList();
    }

    public Collection<UpdateOperationHandler> updateHandlers() {
        var handlers = new ArrayList<UpdateOperationHandler>();
        handlers.add(new SqlUpdateOperation(context, objectClass, support));
        children.forEach(child -> handlers.add(updateHandler(child)));
        return List.copyOf(handlers);
    }

    public DeleteOperationHandler deleteHandler() {
        return new SqlDeleteOperation(context, objectClass, support);
    }

    public Collection<DeleteOperationHandler> deleteCleanupHandlers() {
        var handlers = new ArrayList<DeleteOperationHandler>();
        for (var child : children) {
            handlers.add(cleanup((uid, operationContext) -> {
                var sql = operationContext.get(SqlWriteContext.class);
                new SqlChildTableWriteHandler(context, child)
                        .delete(sql.connection(), sql.parentValues(uid));
            }));
        }
        for (var config : objectClass.junctionJoinConfigs()) {
            handlers.add(cleanup((uid, operationContext) -> {
                var sql = operationContext.get(SqlWriteContext.class);
                var parentValues = sql.parentValues(uid);
                var junction = new SqlTableAccess(context, config.junctionTable(), "jd");
                var criteria = new LinkedHashMap<String, Object>();
                for (var key : config.parentJoinKeys()) {
                    criteria.put(junction.actualColumn(key.childColumn()),
                            junction.toWireValue(key.childColumn(), parentValues.get(key.parentColumn())));
                }
                junction.delete(sql.connection(), criteria);
            }));
        }
        return List.copyOf(handlers);
    }

    private AttributeCreateOperationHandler createHandler(SqlChildJoinConfig child) {
        return new AttributeCreateOperationHandler() {
            @Override
            public Capability<Attribute, AttributeCreateOperationHandler> canHandle(
                    Collection<Attribute> attributes, OperationOptions options) {
                return new Capability<>(this, attributes.stream()
                        .filter(attribute -> child.targetAttributeName().equalsIgnoreCase(attribute.getName())).toList());
            }

            @Override
            public void create(Request request, OperationOptions options, ContextLookup operationContext) {
                request.attributes().forEach(attribute ->
                        support.requireRelatedAttributeWritable(attribute.getName(), true));
                var sql = operationContext.get(SqlWriteContext.class);
                // Keep the writer's mutable column-path cache local to this execution.
                new SqlChildTableWriteHandler(context, child)
                        .create(sql.connection(), sql.parentValues(request.uid()), request.attributes());
            }
        };
    }

    private UpdateOperationHandler updateHandler(SqlChildJoinConfig child) {
        return new UpdateOperationHandler() {
            @Override
            public Capability<AttributeDelta, UpdateOperationHandler> canHandle(
                    Collection<AttributeDelta> modifications, OperationOptions options) {
                return new Capability<>(this, modifications.stream()
                        .filter(delta -> child.targetAttributeName().equalsIgnoreCase(delta.getName())).toList());
            }

            @Override
            public boolean requiresOriginalState() {
                // Child state is read directly from its table using the operation connection.
                return false;
            }

            @Override
            public void update(UpdateRequest request, OperationOptions options, ContextLookup operationContext) {
                request.attributeDeltaSet().forEach(delta ->
                        support.requireRelatedAttributeWritable(delta.getName(), false));
                var sql = operationContext.get(SqlWriteContext.class);
                new SqlChildTableWriteHandler(context, child)
                        .update(sql.connection(), sql.parentValues(request.uid()), request.attributeDeltaSet());
            }
        };
    }

    private DeleteOperationHandler cleanup(DeleteStep step) {
        return new DeleteOperationHandler() {
            @Override
            public void delete(Uid uid, OperationOptions options, ContextLookup operationContext) {
                step.delete(uid, operationContext);
            }
        };
    }

    @FunctionalInterface
    private interface DeleteStep {
        void delete(Uid uid, ContextLookup context);
    }
}
