/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.yaml.binding;

import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.yaml.decl.LocatedDocument;
import com.evolveum.polygon.conndev.yaml.decl.LocatedNode;
import com.evolveum.polygon.sql.base.build.api.SqlTypeSpecification;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The YAML {@code sql: { type: ... }} coercer: size/precision in parentheses is dropped, the base
 * name is matched strictly (case-insensitively) against the built-in names and aliases, and an
 * unrecognized name fails with {@code Unknown SQL type} (no silent fallback).
 */
public class SqlTypeCoercerTest {

    private final SqlTypeCoercer coercer = new SqlTypeCoercer();

    private SqlSchemaValueMapping coerce(String typeName) {
        var document = LocatedDocument.parse("test.yaml", "type: " + typeName);
        LocatedNode node = document.root().get("type");
        SourceLocation location = document.location(node.line(), node.col());
        return (SqlSchemaValueMapping) ((SqlTypeSpecification)
                coercer.coerce(node, location, SqlTypeSpecification.class)).mapping();
    }

    @Test
    public void dropsSizeAndPrecision() {
        assertThat(coerce("VARCHAR(100)")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(coerce("NUMBER(10, 2)")).isEqualTo(SqlSchemaValueMapping.NUMERIC);
        assertThat(coerce("TIMESTAMP(6)")).isEqualTo(SqlSchemaValueMapping.TIMESTAMP);
        assertThat(coerce("varchar(50)")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
    }

    @Test
    public void matchesDatabaseAliases() {
        assertThat(coerce("NVARCHAR(50)")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(coerce("INT8")).isEqualTo(SqlSchemaValueMapping.BIGINT);
        assertThat(coerce("TEXT")).isEqualTo(SqlSchemaValueMapping.CLOB);
    }

    @Test
    public void rejectsUnknownNames() {
        assertThatThrownBy(() -> coerce("VARCHR(100)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown SQL type 'VARCHR(100)'");
        // substring look-alikes no longer fuzzy-match
        assertThatThrownBy(() -> coerce("INTERVAL(2, 0)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown SQL type 'INTERVAL(2, 0)'");
    }
}
