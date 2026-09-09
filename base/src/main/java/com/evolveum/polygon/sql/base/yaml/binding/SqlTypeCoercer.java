/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.yaml.binding;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.yaml.decl.DeclYamlValueParser;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.sql.base.build.api.SqlTypeSpecification;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;

/**
 * Coerces a {@code type} value in an attribute's {@code sql:} block (e.g. {@code INT},
 * {@code VARCHAR(50)}, {@code NUMBER(10,2)}) to a {@link SqlTypeSpecification}. The size/precision in
 * parentheses is dropped — the built-in type specifications are size-agnostic (they map to a single
 * {@link SqlSchemaValueMapping}), exactly like the Groovy {@code sql { type VARCHAR(50) }} DSL.
 */
public final class SqlTypeCoercer implements DeclYamlValueParser {

    @Override
    public Object coerce(LocatedNode value, SourceLocation location, Class<?> targetType) {
        var raw = value.text();
        var base = raw.split("\\(", 2)[0].trim();
        var mapping = SqlSchemaValueMapping.fromTypeName(base);
        if (mapping == null) {
            throw new IllegalArgumentException("Unknown SQL type '" + raw + "' at " + location);
        }
        return mapping.asTypeSpecification();
    }
}
