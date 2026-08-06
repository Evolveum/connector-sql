/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.OperationSupportBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlOperationSupportBuilder extends OperationSupportBuilder<SqlOperationSupportBuilder, SqlObjectOperationSupportBuilder> {



    @Override
    default SqlObjectOperationSupportBuilder objectClass(String className,
                                                         @DelegatesTo(value = SqlOperationSupportBuilder.class, strategy = Closure.DELEGATE_ONLY)
                                                         @Script.Initialization
                                                         Closure<?> closure) {
        return OperationSupportBuilder.super.objectClass(className, closure);
    }
}
