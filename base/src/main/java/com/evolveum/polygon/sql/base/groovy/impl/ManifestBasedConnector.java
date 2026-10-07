/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.groovy.GroovySchemaLoader;
import com.evolveum.polygon.conndev.groovy.GroovyScriptLoader;
import com.evolveum.polygon.conndev.spi.ConnectorManifest;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import org.identityconnectors.framework.spi.ConnectorClass;

import java.util.Collection;
import java.util.List;

/**
 * Zero-code SQL connector that loads all schema and operation scripts
 * from a {@code connector.manifest.json} or {@code connector.manifest.yaml}/{@code .yml} file
 * (exactly one of the three must be bundled).
 *
 * <p>Scripts are loaded once (not reinitialized on each call).
 * Place groovy or YAML script files on the classpath and reference them in the manifest.</p>
 *
 * <p>Example connector.manifest.json:
 * <pre>{@code
 * {
 *   "connector": {
 *     "schema": [
 *       { "script": "/schema/app_user.groovy" },
 *       { "script": "/schema/app_group.groovy" }
 *     ],
 *     "operation": [
 *       { "script": "/handlers/app_user.groovy" }
 *     ]
 *   }
 * }
 * }</pre>
 */
@ConnectorClass(displayNameKey = "manifest.connector.display", configurationClass = SqlConnectorConfiguration.class, messageCatalogPaths = "Messages")
public class ManifestBasedConnector extends AbstractGroovySqlConnector {

    private static final String CONNECTOR_MANIFEST = "/connector.manifest";
    private final ConnectorManifest manifest;

    public ManifestBasedConnector() {
        this(CONNECTOR_MANIFEST);
    }

    /**
     * @param manifestBaseName classpath resource base name (without {@code .yaml}/{@code .yml}/
     *                          {@code .json}) of the manifest to load — for connectors/tests whose
     *                          manifest lives elsewhere than the classpath root.
     */
    protected ManifestBasedConnector(String manifestBaseName) {
        super(false);
        this.manifest = ConnectorManifest.load(getClass(), manifestBaseName);
    }

    @Override
    protected void initializeSchema(GroovySchemaLoader loader) {
        manifest.schemaScripts().forEach(loader::loadFromResource);
    }

    @Override
    protected void initializeObjectClassHandler(GroovyScriptLoader builder) {
        manifest.operationScripts().forEach(builder::loadFromResource);
    }

    @Override
    protected List<String> schemaResources(Collection<String> excludedResources) {
        return manifest.schemaScripts(excludedResources);
    }

    @Override
    protected List<String> operationResources(Collection<String> excludedResources) {
        return manifest.operationScripts(excludedResources);
    }
}