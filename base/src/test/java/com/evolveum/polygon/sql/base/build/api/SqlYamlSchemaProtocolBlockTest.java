/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.expectThrows;

/**
 * The {@code sql:} top-level YAML block is connector-sql's counterpart of the Groovy
 * {@code sql { table "..." } } DSL — it drives the same {@link SqlObjectClassSchemaBuilderImpl#sql()}
 * mapping declaratively, via {@code @Yaml.Sub} on {@link SqlObjectClassSchemaBuilder#sql()} and
 * {@code @Yaml.Key} on {@code SqlMapping.table(String)}/{@code schema(String)}.
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
                objectClasses:
                  Person:
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
                objectClasses:
                  Person:
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
                objectClasses:
                  Person:
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
                objectClasses:
                  Person:
                    scim:
                      path: /Users
                """));
        assertThat(exception.getMessage()).contains("scim");
    }

    /**
     * An attribute-level {@code sql:} block binds {@code type}/{@code primaryKey}/{@code autoIncrement}
     * onto the live builder — the declarative counterpart of the Groovy {@code sql { type INT }} DSL.
     */
    @Test
    public void attributeSqlBlockBindsTypeAndColumnMetadata() {
        var schemaBuilder = schemaBuilder();
        new YamlSchemaLoader(schemaBuilder).load("""
                objectClasses:
                  Employee:
                    sql:
                      table: emp
                    attributes:
                      id:
                        connId:
                          name: __UID__
                        sql:
                          type: INT
                          primaryKey: true
                          autoIncrement: true
                      quantity:
                        sql:
                          type: BIGINT
                """);
        schemaBuilder.applyStructuralRules();
        var definition = schemaBuilder.build().objectClass(new ObjectClass("Employee"));
        var path = definition.sql().pathAlias("o");
        var id = (SqlAttributeMapping.SingleColumn) definition.attributeFromConnIdName(Uid.NAME).sql();
        assertThat(id.dslPath(path).getType()).isEqualTo(Integer.class);
        var quantity = (SqlAttributeMapping.SingleColumn) definition.attributeFromConnIdName("quantity").sql();
        assertThat(quantity.dslPath(path).getType()).isEqualTo(BigInteger.class);
    }

    /**
     * The {@code primaryKey}/{@code autoIncrement}/{@code notNull} flags in an attribute-level
     * {@code sql:} block are not just stored — they drive the same ConnId schema effects as their
     * auto-detected counterparts ({@code PrimaryKeyIsNotUpdatableRule},
     * {@code AutoIncrementColumnIsNotEditableRule}, {@code NullableAttributesAreNotRequiredRule}).
     */
    @Test
    public void attributeSqlFlagsDriveConnIdMetadata() {
        var schemaBuilder = schemaBuilder();
        new YamlSchemaLoader(schemaBuilder).load("""
                objectClasses:
                  Employee:
                    sql:
                      table: emp
                    attributes:
                      id:
                        connId:
                          name: __UID__
                        sql:
                          type: INT
                          primaryKey: true
                          autoIncrement: true
                      email:
                        sql:
                          type: VARCHAR
                          notNull: true
                """);
        schemaBuilder.applyStructuralRules();
        var definition = schemaBuilder.build().objectClass(new ObjectClass("Employee"));

        var id = definition.attributeFromConnIdName(Uid.NAME).connId();
        assertThat(id.isCreateable()).isFalse();
        assertThat(id.isUpdateable()).isFalse();

        var email = definition.attributeFromConnIdName("email").connId();
        assertThat(email.isRequired()).isTrue();
    }
}
