/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.build.api.DeleteOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.groovy.AbstractDeleteOperationBuilder;
import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.write.SqlDeleteOperation;

public final class SqlDeleteOperationBuilderImpl extends AbstractDeleteOperationBuilder<SqlObjectClassDefinition> {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;

    SqlDeleteOperationBuilderImpl(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public boolean isEnabled() {
        return enabled.value();
    }

    @Override
    public DeleteOperationBuilder enabled(DefinitionValue<Boolean> value) {
        enabled = enabled.moreSpecific(value);
        return this;
    }

    @Override
    public ObjectDeleteOperation build() {
        return new SqlDeleteOperation(context, objectClass);
    }
}
