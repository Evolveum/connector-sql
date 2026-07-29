/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.expectThrows;

/**
 * The {@code sql:} top-level YAML block is connector-sql's counterpart of the Groovy
 * {@code sql { table "..." } } DSL — it drives the same {@link SqlObjectClassSchemaBuilderImpl#sql()}
 * mapping via the generic conndev {@code YamlProtocolBlockConsumer} hook.
 */
public class SqlYamlSchemaProtocolBlockTest {

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration c) { }
        @Override public void dispose() { }
    }

    private static SqlSchemaBuilderImpl schemaBuilder() {
        return new SqlSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
    }

    @Test
    public void sqlBlockSetsTableAndSchema() {
        var schemaBuilder = schemaBuilder();
        new YamlSchemaLoader(schemaBuilder).load("""
                objectClass: Person
                sql:
                  table: app_user
                  schema: public
                attributes:
                  user_id:
                    connId:
                      name: __UID__
                """);

        var person = schemaBuilder.objectClass("Person");
        assertThat(person.sql().table()).isEqualTo("app_user");
        assertThat(person.sql().schema()).isEqualTo("public");
    }

    @Test
    public void sqlBlockWithOnlyTableLeavesSchemaEmpty() {
        var schemaBuilder = schemaBuilder();
        new YamlSchemaLoader(schemaBuilder).load("""
                objectClass: Person
                sql:
                  table: app_user
                """);

        var person = schemaBuilder.objectClass("Person");
        assertThat(person.sql().table()).isEqualTo("app_user");
        assertThat(person.sql().schema()).isNull();
    }

    /** A typo'd sub-key inside the sql block fails fast, exactly like a typo in attributes. */
    @Test
    public void unknownKeyInsideSqlBlockFailsFast() {
        var loader = new YamlSchemaLoader(schemaBuilder());

        var exception = expectThrows(IllegalArgumentException.class, () -> loader.load("""
                objectClass: Person
                sql:
                  tabel: app_user
                """));
        assertThat(exception.getMessage()).contains("tabel");
    }

    /** An unrecognized protocol block name (not "sql") fails fast — only "sql" is understood. */
    @Test
    public void unknownProtocolBlockNameFailsFast() {
        var loader = new YamlSchemaLoader(schemaBuilder());

        var exception = expectThrows(IllegalArgumentException.class, () -> loader.load("""
                objectClass: Person
                scim:
                  path: /Users
                """));
        assertThat(exception.getMessage()).contains("scim");
    }
}
