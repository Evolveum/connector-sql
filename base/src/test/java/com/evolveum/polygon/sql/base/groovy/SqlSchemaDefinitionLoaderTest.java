/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.expectThrows;

/**
 * {@link SqlSchemaDefinitionLoader} dispatches {@code .yaml}/{@code .yml} resources to the generic
 * conndev {@code YamlSchemaLoader} bound directly onto the live {@link SqlSchemaBuilderImpl} — the
 * same builder the Groovy DSL populates — and falls back from a conventional {@code .groovy} name to
 * a {@code .yaml}/{@code .yml} file of the same base name, exactly like connector-scimrest's
 * {@code SchemaDefinitionLoader}.
 */
public class SqlSchemaDefinitionLoaderTest {

    private SqlSchemaBuilderImpl builder;

    private SqlSchemaDefinitionLoader newLoader() {
        var config = new SqlConnectorConfiguration();
        var context = new SqlBaseContext(config);
        builder = new SqlSchemaBuilderImpl(ManifestBasedConnector.class, context);
        return new SqlSchemaDefinitionLoader(context, builder, config.groovyContext());
    }

    @Test
    public void loadsYamlSchemaDirectlyOntoTheLiveBuilder() {
        var loader = newLoader();
        loader.loadFromResource("/yaml-schema/Person.native.schema.yaml");

        assertThat(builder.objectClass("Person").sql().table()).isEqualTo("app_user");
    }

    /** Referenced by its conventional .groovy name; only a .yaml file with the same base name exists. */
    @Test
    public void fallsBackToYamlWhenGroovyNameIsMissing() {
        var loader = newLoader();
        loader.loadFromResource("/yaml-schema/Person.native.schema.groovy");

        assertThat(builder.objectClass("Person").sql().table()).isEqualTo("app_user");
    }

    @Test
    public void missingBothGroovyAndYamlFailsFast() {
        var loader = newLoader();

        var exception = expectThrows(IllegalArgumentException.class,
                () -> loader.loadFromResource("/yaml-schema/DoesNotExist.native.schema.groovy"));
        assertThat(exception.getMessage()).contains("DoesNotExist");
    }
}
