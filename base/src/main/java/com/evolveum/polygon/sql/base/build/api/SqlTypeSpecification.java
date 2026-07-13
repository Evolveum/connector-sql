package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Groovy;

public abstract class SqlTypeSpecification {

    public interface Mixin {

        @Groovy.Convenience SqlTypeSpecification INT = null;

        @Groovy.Convenience SqlTypeSpecification BIGINT = null;

        @Groovy.Convenience SqlTypeSpecification SMALLINT = null;

        @Groovy.Convenience SqlTypeSpecification TINYINT = null;

        @Groovy.Convenience SqlTypeSpecification BOOLEAN = null;

        @Groovy.Convenience SqlTypeSpecification VARCHAR(int size);

        @Groovy.Convenience SqlTypeSpecification INTEGER = null;

    }

    public abstract String getTypeName();
}