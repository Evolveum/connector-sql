/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.annotations.Groovy;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;

public interface  SqlTypeSpecification {

    public interface Mixin {

        @Groovy.Convenience SqlTypeSpecification INT = SqlSchemaValueMapping.INTEGER.asTypeSpecification();

        @Groovy.Convenience SqlTypeSpecification BIGINT = SqlSchemaValueMapping.BIGINT.asTypeSpecification();

        @Groovy.Convenience SqlTypeSpecification SMALLINT = SqlSchemaValueMapping.SMALLINT.asTypeSpecification();

        @Groovy.Convenience SqlTypeSpecification TINYINT = SqlSchemaValueMapping.TINYINT.asTypeSpecification();

        @Groovy.Convenience SqlTypeSpecification BOOLEAN = SqlSchemaValueMapping.BOOLEAN.asTypeSpecification();

        @Groovy.Convenience SqlTypeSpecification DATE = SqlSchemaValueMapping.DATE.asTypeSpecification();

        @Groovy.Convenience
        @SuppressWarnings("java:S100")
        default SqlTypeSpecification VARCHAR(int size) {
            return SqlSchemaValueMapping.VARCHAR.asTypeSpecification();
        }

        @Groovy.Convenience
        @SuppressWarnings("java:S100")
        default SqlTypeSpecification NUMBER(int precision) {
            return SqlSchemaValueMapping.NUMERIC.asTypeSpecification();
        }

        @Groovy.Convenience
        @SuppressWarnings("java:S100")
        default SqlTypeSpecification NUMBER(int precision, int scale) {
            return SqlSchemaValueMapping.NUMERIC.asTypeSpecification();
        }

        @Groovy.Convenience
        @SuppressWarnings("java:S100")
        default SqlTypeSpecification VARCHAR2(int size) {
            return SqlSchemaValueMapping.VARCHAR.asTypeSpecification();
        }

        @Groovy.Convenience
        @SuppressWarnings("java:S100")
        default SqlTypeSpecification TIMESTAMP(int precision) {
            return SqlSchemaValueMapping.TIMESTAMP.asTypeSpecification();
        }

        @SuppressWarnings({"java:S100", "java:S1845"})
        default SqlTypeSpecification DATE(int precision) {
            return SqlSchemaValueMapping.DATE.asTypeSpecification();
        }

        @Groovy.Convenience SqlTypeSpecification INTEGER = SqlSchemaValueMapping.INTEGER.asTypeSpecification();

    }

    abstract String getTypeName();

    abstract SqlValueMapping.SingleColumn mapping();


    record BuiltIn(String type, SqlSchemaValueMapping mapping) implements SqlTypeSpecification {

        public BuiltIn(SqlSchemaValueMapping mapping) {
            this(mapping.name(), mapping);
        }

        @Override
        public String getTypeName() {
            return  type();
        }
    }
}