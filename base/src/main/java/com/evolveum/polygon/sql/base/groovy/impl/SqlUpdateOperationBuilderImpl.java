/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.groovy.AbstractUpdateOperationBuilder;
import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.write.SqlUpdateOperation;

public final class SqlUpdateOperationBuilderImpl extends AbstractUpdateOperationBuilder<SqlObjectClassDefinition> {

    private final SqlBaseContext context;
    private final SqlObjectClassDefinition objectClass;
    private DefinitionValue<Boolean> enabled = DefinitionValue.DEFAULT_TRUE;

    SqlUpdateOperationBuilderImpl(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    @Override
    public boolean isEnabled() {
        return enabled.value();
    }

    @Override
    public UpdateOperationBuilder enabled(DefinitionValue<Boolean> value) {
        enabled = enabled.moreSpecific(value);
        return this;
    }

    @Override
    public ObjectUpdateOperation build() {
        return new SqlUpdateOperation(context, objectClass);
    }
}
