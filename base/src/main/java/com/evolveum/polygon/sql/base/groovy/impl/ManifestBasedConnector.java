/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy.impl;

import com.evolveum.polygon.conndev.spi.ConnectorManifest;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlGroovySchemaLoader;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerBuilder;
import org.identityconnectors.framework.spi.ConnectorClass;

/**
 * Zero-code SQL connector that loads all schema and operation scripts
 * from a {@code connector.manifest.json} file.
 *
 * <p>Scripts are loaded once (not reinitialized on each call).
 * Place groovy script files on the classpath and reference them in the manifest.</p>
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
@ConnectorClass(displayNameKey = "manifest.sql.connector.display", configurationClass = SqlConnectorConfiguration.class)
public class ManifestBasedConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {

    private static final String CONNECTOR_MANIFEST = "/connector.manifest.json";
    private final ConnectorManifest manifest;

    public ManifestBasedConnector() {
        super(false);
        var resource = getClass().getResourceAsStream(CONNECTOR_MANIFEST);
        this.manifest = new ConnectorManifest(resource);
    }

    @Override
    protected void initializeSchema(SqlGroovySchemaLoader loader) {
        manifest.schemaScripts().forEach(s -> loader.loadFromResource(s));
    }

    @Override
    protected void initializeObjectClassHandler(SqlHandlerBuilder builder) {
        manifest.operationScripts().forEach(builder::loadFromResource);
    }
}