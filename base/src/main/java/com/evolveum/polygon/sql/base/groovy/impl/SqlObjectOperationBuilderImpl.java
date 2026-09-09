/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.BaseObjectOperationSupportBuilder;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;
import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;
import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;
import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;
import com.evolveum.polygon.conndev.annotations.Yaml;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectOperationSupportBuilder;
import com.evolveum.polygon.sql.base.write.SqlWriteHandlers;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

public class SqlObjectOperationBuilderImpl extends BaseObjectOperationSupportBuilder<
        SqlSearchOperationBuilderImpl,
        SqlCreateOperationBuilderImpl,
        SqlUpdateOperationBuilderImpl,
        SqlDeleteOperationBuilderImpl,
        SqlObjectClassDefinition
        > implements SqlObjectOperationSupportBuilder {

    private final SqlSearchOperationBuilderImpl search;
    private final SqlCreateOperationBuilderImpl create;
    private final SqlUpdateOperationBuilderImpl update;
    private final SqlDeleteOperationBuilderImpl delete;

    public SqlObjectOperationBuilderImpl(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        super(context, objectClass);
        this.search = new SqlSearchOperationBuilderImpl(this, context, objectClass);
        var writes = new SqlWriteHandlers(context, objectClass);
        this.create = new SqlCreateOperationBuilderImpl(this, writes);
        this.update = new SqlUpdateOperationBuilderImpl(this, writes);
        this.delete = new SqlDeleteOperationBuilderImpl(this, writes);

        if (Boolean.TRUE.equals(objectClass.getReadOnly())) {
            disableCreate();
            disableUpdate();
            disableDelete();
        }
    }

    public boolean isCreateDisabled() {
        return !create.isEnabled();
    }

    public boolean isUpdateDisabled() {
        return !update.isEnabled();
    }

    public boolean isDeleteDisabled() {
        return !delete.isEnabled();
    }

    @Override
    public SqlObjectOperationSupportBuilder disableCreate() {
        create.enabled(detected(false));
        return this;
    }

    @Override
    public SqlObjectOperationSupportBuilder disableUpdate() {
        update.enabled(detected(false));
        return this;
    }

    @Override
    public SqlObjectOperationSupportBuilder disableDelete() {
        delete.enabled(detected(false));
        return this;
    }

    @Override
    @Yaml.Sub
    public SqlSearchOperationBuilderImpl search() {
        return search;
    }

    @Override
    @Yaml.Sub
    public SqlCreateOperationBuilderImpl create() {
        return create;
    }

    @Override
    @Yaml.Sub
    public SqlUpdateOperationBuilderImpl update() {
        return update;
    }

    @Override
    @Yaml.Sub
    public SqlDeleteOperationBuilderImpl delete() {
        return delete;
    }

    public SqlObjectOperationBuilderImpl create(ObjectCreateOperation operation) {
        return register(ObjectCreateOperation.class, operation);
    }

    public SqlObjectOperationBuilderImpl update(ObjectUpdateOperation operation) {
        return register(ObjectUpdateOperation.class, operation);
    }

    public SqlObjectOperationBuilderImpl delete(ObjectDeleteOperation operation) {
        return register(ObjectDeleteOperation.class, operation);
    }

    @Override
    public <T extends ObjectClassOperation> SqlObjectOperationBuilderImpl register(
            Class<T> operationType, T operation) {
        registerOperation(operationType, operation);
        return this;
    }

    public SqlBaseContext getContext() {
        return (SqlBaseContext) context;
    }
}
