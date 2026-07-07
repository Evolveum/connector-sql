package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Groovy;

public class SqlTypeSpecification {

    public interface Mixin {

        @Groovy.Convenience
        SqlTypeSpecification INT = null;

        @Groovy.Convenience
        SqlTypeSpecification TIMESTAMP = null;


        @Groovy.Convenience
        SqlTypeSpecification VARCHAR(int size);

    }
}
