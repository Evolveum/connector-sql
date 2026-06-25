package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.annotations.Groovy;

public class SqlTypeSpecification {

    public interface Mixin {

        @Groovy.Convenience
        SqlTypeSpecification INT = null;

        @Groovy.Convenience
        SqlTypeSpecification TIMESTAMP = null;


        SqlTypeSpecification VARCHAR(int size);

    }
}
