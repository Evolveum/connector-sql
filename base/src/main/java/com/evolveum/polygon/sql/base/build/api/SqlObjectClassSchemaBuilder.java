/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.sql.base.build.api.SyncOperationSchemaBuilder;
import com.evolveum.polygon.sql.base.sync.SyncStrategy;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Schema builder for a SQL object class, extended from the ConnId object class schema builder.
 *
 * <p>Provides SQL-specific mapping (table, schema) and attribute-level SQL metadata through
 * the nested {@link SqlMapping} interface.
 *
 * @see SqlObjectClassSchemaBuilderImpl
 * @see SqlAttributeBuilder
 */
public interface SqlObjectClassSchemaBuilder extends ObjectClassSchemaBuilder<SqlObjectClassSchemaBuilder, SqlAttributeBuilder<SqlAttributeBuilder.Reference>, SqlAttributeBuilder.Reference> {

    /**
     * Returns the SQL mapping for this object class.
     */
    SqlMapping sql();

    /**
     * Sets SQL mapping using a Groovy closure.
     */
    default SqlMapping sql(@Script.Initialization
                                     @DelegatesTo(value = SqlMapping.class, strategy = Closure.DELEGATE_ONLY)
                                     Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, sql());
    }

    @Override
    default SqlAttributeBuilder<SqlAttributeBuilder.Reference> attribute(String name,
                               @Script.Initialization
                               @DelegatesTo(value = SqlAttributeBuilder.class, strategy = Closure.DELEGATE_ONLY)
                               Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

/**
     * Returns the sync operation builder for this object class.
     *
     * <p>Used in Groovy scripts to configure sync behavior per-table:
     */
    SyncOperationSchemaBuilder sync();

    /**
     * Configures the sync operation using a Groovy closure.
     */
    default SyncOperationSchemaBuilder sync(@Script.Initialization
                                      @DelegatesTo(value = SyncOperationSchemaBuilder.class, strategy = Closure.DELEGATE_ONLY)
                                      Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, sync());
    }

    /**
     * Sets whether only explicitly defined attributes should be detected.
     * When true, JDBC detection will only include columns that have an explicit
     * Groovy-defined attribute.
     * Default is {@code false}.
     *
     * @param value whether to only include explicitly listed attributes
     * @return this builder for chaining
     */
    SqlObjectClassSchemaBuilder onlyExplicitlyListed(boolean value);

    /**
     * Returns the current value of the onlyExplicitlyListed flag.
     *
     * @return the flag value
     */
    Boolean getOnlyExplicitlyListed();

    /**
     * Convenience method to set the SQL table name.
     *
     * @param table the SQL table name
     * @return this SqlMapping for chaining
     */
    default SqlMapping table(String table) {
        return sql().table(DefinitionValue.from(table, SourceLocation.capture()));
    }

    /**
     * Convenience method to set the SQL schema name.
     *
     * @param schema the SQL schema name
     * @return this SqlMapping for chaining
     */
    default SqlMapping schema(String schema) {
        return sql().schema(DefinitionValue.from(schema, SourceLocation.capture()));
    }

    /**
     * SQL mapping interface for object classes.
     * Maps ConnId object classes to SQL tables.
     */
    interface SqlMapping {

        /** Sets the SQL table name (String version). */
        void table(String table);

        /** Returns the SQL schema name (empty string if not set). */
        String schema();

        /** Returns the SQL table name. */
        String table();

        /** Sets the SQL schema name. */
        SqlMapping schema(DefinitionValue<String> detected);

        /** Sets the SQL table name. */
        SqlMapping table(DefinitionValue<String> table);

    }

}