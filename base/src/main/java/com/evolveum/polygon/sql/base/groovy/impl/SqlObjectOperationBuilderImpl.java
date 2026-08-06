/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.build.api.CreateOperationBuilder;
import com.evolveum.polygon.conndev.build.api.DeleteOperationBuilder;
import com.evolveum.polygon.conndev.build.api.UpdateOperationBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.groovy.AbstractCreateOperationBuilder;
import com.evolveum.polygon.conndev.groovy.AbstractDeleteOperationBuilder;
import com.evolveum.polygon.conndev.groovy.AbstractUpdateOperationBuilder;
import com.evolveum.polygon.conndev.groovy.BaseObjectOperationSupportBuilder;
import com.evolveum.polygon.conndev.spi.ObjectCreateOperation;
import com.evolveum.polygon.conndev.spi.ObjectDeleteOperation;
import com.evolveum.polygon.conndev.spi.ObjectUpdateOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectOperationSupportBuilder;

public class SqlObjectOperationBuilderImpl extends BaseObjectOperationSupportBuilder<
        SqlSearchOperationBuilderImpl,
        AbstractCreateOperationBuilder,
        AbstractUpdateOperationBuilder,
        AbstractDeleteOperationBuilder
        > implements SqlObjectOperationSupportBuilder {

    private final SqlSearchOperationBuilderImpl search;

    public SqlObjectOperationBuilderImpl(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        super(context, objectClass);
        this.search = new SqlSearchOperationBuilderImpl(this, context, objectClass);

    }

    @Override
     public SqlSearchOperationBuilderImpl search() {
         return search;
     }

     @Override
     public AbstractCreateOperationBuilder create() {
         // Not supported
         return  new AbstractCreateOperationBuilder() {
             @Override
             public boolean isEnabled() {
                 return false;
             }

             @Override
             public CreateOperationBuilder enabled(DefinitionValue<Boolean> value) {
                 return null;
             }

             @Override
             public ObjectCreateOperation build() {
                 return null;
             }
         };
     }

     @Override
     public AbstractUpdateOperationBuilder update() {
         // Not supported
        return new AbstractUpdateOperationBuilder() {

            @Override
            public ObjectUpdateOperation build() {
                return null;
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public UpdateOperationBuilder enabled(DefinitionValue<Boolean> value) {
                return null;
            }
        };
     }

     @Override
     public AbstractDeleteOperationBuilder delete() {
         // Not supported
        return new AbstractDeleteOperationBuilder() {
             @Override
             public boolean isEnabled() {
                 return false;
             }

             @Override
             public DeleteOperationBuilder enabled(DefinitionValue<Boolean> value) {
                 return null;
             }

             @Override
             public ObjectDeleteOperation build() {
                 return null;
             }
         };
     }
 }
