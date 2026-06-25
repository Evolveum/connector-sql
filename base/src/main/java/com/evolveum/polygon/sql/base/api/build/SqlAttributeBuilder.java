package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.AttributeBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlAttributeBuilder extends AttributeBuilder {

    SqlMapping sql();

    default SqlMapping sql(@Script.Initialization
                           @DelegatesTo(value = SqlMapping.class, strategy = Closure.DELEGATE_ONLY)
                           Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, sql());
    }

    interface SqlMapping extends SqlTypeSpecification.Mixin {

        SqlMapping name(String name);

        SqlMapping type(SqlTypeSpecification typeSpecification);

        SqlMapping notNull(boolean notNull);
        SqlMapping unique(boolean unique);

    }
}
