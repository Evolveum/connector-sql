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
import com.evolveum.polygon.sql.base.search.SqlCustomQueryBuilderContext;
import com.evolveum.polygon.sql.base.search.SqlWherePredicateBuilder;
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

        /**
         * Adds a custom SQL query handler.
         * <p>This allows full control over SELECT, FROM, WHERE and ORDER BY clauses
         * via Groovy closures.</p>
         */
        SqlCustomSearchOperationBuilder custom();

        default SqlCustomSearchOperationBuilder custom(
                @Script.Initialization
                @DelegatesTo(value = SqlCustomQueryBuilderContext.class,
                             strategy = Closure.DELEGATE_FIRST)
                Closure<?> closure) {
            if (closure == null) return custom();
            var b = custom();
            b.query(closure);
            return b;
        }
    }

    /**
     * Builder for custom SQL search operations.
     * Configures a Groovy closure that defines SELECT, FROM, WHERE, ORDER BY.
     */
    interface SqlCustomSearchOperationBuilder extends Fluent<SqlCustomSearchOperationBuilder> {

        /** 
         * Sets the query closure.
         * The closure receives a SqlCustomQueryBuilderContext.
         */
        SqlCustomSearchOperationBuilder query(
                @DelegatesTo(value = SqlCustomQueryBuilderContext.class,
                             strategy = Closure.DELEGATE_FIRST)
                Closure<?> closure);

        /** Controls whether this handler supports empty (null) filter. */
        SqlCustomSearchOperationBuilder emptyFilterSupported(boolean value);
    }

    interface BuiltIn extends Fluent<BuiltIn> {

        BuiltIn enabled(boolean value);

        /**
         * Adds a WHERE clause via Groovy closure.
         * <p>Example: {@code where { e -> e.col('legacy').eq(false) }}</p>
         * <p>Or with property-style: {@code where { e -> e.legacy == false }}</p>
         */
        BuiltIn where(
                @DelegatesTo(value = SqlWherePredicateBuilder.class, strategy = Closure.DELEGATE_FIRST)
                Closure<?> closure);
    }

}
