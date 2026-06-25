package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlObjectClassSchemaBuilder extends ObjectClassSchemaBuilder {

    SqlMapping sql();

    default SqlMapping sql(@Script.Initialization
                                    @DelegatesTo(value = SqlMapping.class, strategy = Closure.DELEGATE_ONLY)
                                    Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, sql());
    }

    @Override
    SqlAttributeBuilder attribute(String name);

    @Override
    default SqlAttributeBuilder attribute(String name,
                               @Script.Initialization
                               @DelegatesTo(value = SqlAttributeBuilder.class, strategy = Closure.DELEGATE_ONLY)
                               Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, attribute(name));
    }

    interface SqlMapping {

        void table(String table);

    }

}
