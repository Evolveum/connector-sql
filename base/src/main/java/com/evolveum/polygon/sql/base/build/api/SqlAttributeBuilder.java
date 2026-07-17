package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.api.AttributeBuilder;
import com.evolveum.polygon.conndev.build.api.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.sql.base.build.spi.SpiSqlAttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

public interface SqlAttributeBuilder<F extends SqlAttributeBuilder<F>> extends AttributeBuilder<F, SqlAttributeDefinition> {

    SqlMapping sql();

    default SqlMapping sql(@Script.Initialization
                           @DelegatesTo(value = SqlMapping.class, strategy = Closure.DELEGATE_ONLY)
                           Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, sql());
    }

    interface SqlMapping extends SqlTypeSpecification.Mixin, SpiSqlAttributeBuilder.SqlMapping {

        default SqlMapping name(String name) {
            return column(DefinitionValue.from(name, SourceLocation.capture()));
        }

        default SqlMapping type(SqlTypeSpecification typeSpecification) {
            return type(DefinitionValue.from(typeSpecification, SourceLocation.capture()));
        }

        default SqlMapping notNull(boolean notNull) {
            return notNull(DefinitionValue.from(notNull, SourceLocation.capture()));
        }

        default SqlMapping notNull() {
            return notNull(true);
        }

        default SqlMapping nullable() {
            return notNull(false);
        }

        default SqlMapping unique(boolean unique) {
            return unique(DefinitionValue.from(unique, SourceLocation.capture()));
        }

        default SqlMapping unique() {
            return unique(true);
        }

        default SqlMapping primaryKey() {
            return primaryKey(true);
        }

        default SqlMapping primaryKey(boolean value) {
            return primaryKey(DefinitionValue.from(value, SourceLocation.capture()));
        }

        default SqlMapping autoIncrement() {
            return autoIncrement(true);
        }

        default SqlMapping autoIncrement(boolean value) {
            return autoIncrement(DefinitionValue.from(value, SourceLocation.capture()));
        }

        SpiSqlAttributeBuilder.SqlUIDMappingBuilder additionalColumns();

        DefinitionValue<String> column();
    }

    interface Reference extends SqlAttributeBuilder<Reference>, ReferenceAttributeBuilder<Reference, SqlAttributeBuilder<Reference>, SqlAttributeDefinition> {

    }
}
