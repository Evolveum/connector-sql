/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.AbstractOperationSupportBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectOperationSupportBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlOperationSupportBuilder;

public class SqlOperationSupportBuilderImpl extends AbstractOperationSupportBuilder<SqlOperationSupportBuilder, SqlObjectOperationSupportBuilder> implements SqlOperationSupportBuilder {

    private final SqlBaseContext context;

    public SqlOperationSupportBuilderImpl(SqlBaseContext context) {
        super(context);
        this.context = context;
    }

    @Override
    protected SqlObjectOperationSupportBuilder newObjectSpecific(BaseObjectClassDefinition classDefinition) {
        return new SqlObjectOperationBuilderImpl(this.context, (SqlObjectClassDefinition) classDefinition);
    }

    @Override
    public SqlObjectOperationBuilderImpl objectClass(String user) {
        return (SqlObjectOperationBuilderImpl) super.objectClass(user);
    }
}
