/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.yaml;

import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlBinder;
import com.evolveum.polygon.sql.base.build.api.SqlOperationSupportBuilder;

import java.io.Reader;

/**
 * The location-aware, engine-driven front-end for SQL operation documents — the YAML counterpart of
 * the Groovy DSL, driving the live {@link SqlOperationSupportBuilder} through the {@code @Yaml.*}
 * binding engine (see {@link DeclYamlBinder}). The document uses the extended envelope: an
 * {@code objectClasses} mapping (object-class name to its {@code search}/{@code create}/{@code
 * update}/{@code delete} blocks). Each block binds onto the corresponding live operation builder, so
 * the YAML operations execute exactly like their Groovy counterparts.
 */
public final class YamlSqlOperationsLoader {

    private final SqlOperationSupportBuilder builder;
    private final GroovyScriptCompiler compiler;

    public YamlSqlOperationsLoader(SqlOperationSupportBuilder builder, GroovyScriptCompiler compiler) {
        this.builder = builder;
        this.compiler = compiler;
    }

    public void load(Reader reader, String sourceName) {
        load(LocatedDocument.parse(sourceName, reader));
    }

    public void load(LocatedDocument document) {
        var binder = new DeclYamlBinder(document, compiler);
        var root = document.root();
        if (root.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("YAML operations document must be a mapping ("
                    + document.sourceName() + ")");
        }
        for (var entry : root.entries()) {
            if (!entry.key().equals("objectClasses")) {
                throw unknownTopLevelKey(document, entry);
            }
            for (var objectClass : requireMap(entry.value(), "objectClasses").entries()) {
                binder.bind(objectClass.value(), builder.objectClass(objectClass.key()));
            }
        }
    }

    private static LocatedNode requireMap(LocatedNode node, String key) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.kind() != LocatedNode.Kind.OBJECT) {
            throw new IllegalArgumentException("'" + key + "' must be a mapping but found a " + node.kind()
                    + " at " + node.line() + ":" + node.col());
        }
        return node;
    }

    private static IllegalArgumentException unknownTopLevelKey(LocatedDocument document, LocatedNode.Entry entry) {
        return new IllegalArgumentException("Unknown top-level key '" + entry.key() + "' in YAML operations document ("
                + document.sourceName() + ":" + entry.keyLine() + ":" + entry.keyCol() + ")");
    }
}
