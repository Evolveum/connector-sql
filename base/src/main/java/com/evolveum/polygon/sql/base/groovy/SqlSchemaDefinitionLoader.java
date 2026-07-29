/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.groovy.GroovyContext;
import com.evolveum.polygon.conndev.yaml.ScriptResources;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Dispatches schema resources to either the Groovy DSL ({@link SqlGroovySchemaLoader}) or the
 * declarative YAML front-end ({@code *.native.schema.yaml}/{@code *.connid.schema.yaml}), by file
 * extension — falling back from a conventional {@code .groovy} resource name to a {@code .yaml}/
 * {@code .yml} file of the same base name when the {@code .groovy} one isn't bundled.
 *
 * <p>Unlike connector-scimrest's {@code SchemaDefinitionLoader}, the YAML schema here is bound
 * directly onto the same live {@link SqlSchemaBuilderImpl} the Groovy DSL populates (not a separate,
 * inert schema) — connector-sql's builder already extends conndev's generic {@code BaseSchemaBuilder}
 * (no forked model), so YAML-declared attributes/{@code sql{}} blocks take effect immediately, merging
 * with auto-discovered columns exactly like Groovy customization scripts do.
 */
public class SqlSchemaDefinitionLoader extends SqlGroovySchemaLoader {

    private final YamlSchemaLoader yamlLoader;

    public SqlSchemaDefinitionLoader(SqlBaseContext context, SqlSchemaBuilderImpl builder, GroovyContext groovyContext) {
        super(context, builder, groovyContext);
        this.yamlLoader = new YamlSchemaLoader(builder);
    }

    @Override
    public void loadResource(String resourceName) {
        var resolved = ScriptResources.resolveWithYamlFallback(getClass(), resourceName);
        if (ScriptResources.isYaml(resolved)) {
            loadYamlFromResource(resolved);
        } else {
            super.loadResource(resolved);
        }
    }

    private void loadYamlFromResource(String resource) {
        var stream = getClass().getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalArgumentException("YAML schema definition resource not found: " + resource);
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            yamlLoader.load(reader, resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read YAML schema definition " + resource, e);
        }
    }
}
