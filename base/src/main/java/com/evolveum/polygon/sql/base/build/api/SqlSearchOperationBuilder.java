/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.SearchOperationBuilder;
import com.evolveum.polygon.conndev.concepts.Fluent;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlSearchOperationBuilder extends SearchOperationBuilder {


    SqlSpecific sql();

    default SqlSpecific sql(
            @Script.Initialization
            @DelegatesTo(value = SqlSpecific.class, strategy = Closure.DELEGATE_ONLY)
            Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, sql());
    }


    interface SqlSpecific extends Fluent<SqlSpecific>  {

        BuiltIn builtIn();

        default BuiltIn builtIn(
                @Script.Initialization
                @DelegatesTo(value = BuiltIn.class, strategy = Closure.DELEGATE_ONLY)
                Closure<?> closure) {
            return GroovyClosures.callAndReturnDelegate(closure, builtIn());
        }
    }

    interface BuiltIn extends Fluent<BuiltIn> {

        BuiltIn enabled(boolean value);

    }

}
