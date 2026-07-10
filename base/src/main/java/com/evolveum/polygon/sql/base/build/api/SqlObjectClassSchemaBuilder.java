package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlObjectClassSchemaBuilder extends ObjectClassSchemaBuilder<SqlObjectClassSchemaBuilder, SqlAttributeBuilder<SqlAttributeBuilder.Reference>, SqlAttributeBuilder.Reference> {

    SqlMapping sql();

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



    interface SqlMapping {

        void table(String table);

        String schema();

        String table();

        SqlMapping schema(DefinitionValue<String> detected);

        SqlMapping table(DefinitionValue<String> table);

        /** Sets the SQL table name as the locator for this object class. */
        default void locator(String locator) {
            // Default: no-op. Implementing classes can override to set the locator.
        }
    }

}
