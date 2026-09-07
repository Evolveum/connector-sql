/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

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
        return new SqlSchemaDefinitionLoader(builder, config.groovyContext());
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

    @DataProvider
    public Object[][] numericSqlTypes() {
        return new Object[][] {
                { "INT", 42 },
                { "BIGINT", BigInteger.valueOf(42) },
                { "NUMBER(10)", new BigDecimal("42") }
        };
    }

    @Test(dataProvider = "numericSqlTypes")
    public void numericIdentifiersStayStringsWhileSqlValuesStayNumeric(String sqlType, Object sqlValue) {
        var loader = newLoader();
        loader.load("""
                objectClass('numeric_identifiers') {
                    attribute('id') {
                        connId { name '__UID__' }
                        sql { type %s }
                    }
                    attribute('display_id') {
                        sql { type %s }
                        connId { name '__NAME__' }
                    }
                    attribute('quantity') {
                        sql { type %s }
                    }
                }
                """.formatted(sqlType, sqlType, sqlType));
        // Match the connector lifecycle: resolve structural types before freezing the schema.
        builder.applyStructuralRules();
        var definition = builder.build().objectClass(new ObjectClass("numeric_identifiers"));
        var table = definition.sql().pathAlias("o");

        for (var identifier : List.of(Uid.NAME, Name.NAME)) {
            var attribute = definition.attributeFromConnIdName(identifier);
            assertThat(attribute.connId().getType()).isEqualTo(String.class);
            var mapping = (SqlAttributeMapping.SingleColumn) attribute.sql();
            assertThat(mapping.dslPath(table).getType()).isEqualTo(sqlValue.getClass());
            assertThat(mapping.singleValueFromAttribute(sqlValue)).isEqualTo("42");
            assertThat(mapping.columnValues(table, "42").getFirst().value()).isEqualTo(sqlValue);
            assertThat(mapping.sqlFilter().eq(table, "42")).isNotNull();
        }

        var quantity = definition.attributeFromConnIdName("quantity");
        assertThat(quantity.connId().getType()).isEqualTo(sqlValue.getClass());
        assertThat(((SqlAttributeMapping.SingleColumn) quantity.sql()).singleValueFromAttribute(sqlValue))
                .isEqualTo(sqlValue);
    }

    @Test
    public void sqlTypeDoesNotOverrideExplicitConnIdType() {
        var loader = newLoader();
        loader.load("""
                objectClass('numeric_identifiers') {
                    attribute('id') {
                        connId { name '__UID__' }
                        sql { type NUMBER(10) }
                    }
                    attribute('external_code') {
                        connId { type String }
                        sql { type NUMBER(10) }
                    }
                }
                """);
        builder.applyStructuralRules();
        var definition = builder.build().objectClass(new ObjectClass("numeric_identifiers"));
        assertThat(definition.attributeFromConnIdName(Name.NAME).connId().getType()).isEqualTo(String.class);
        var attribute = definition.attributeFromConnIdName("external_code");
        assertThat(attribute.connId().getType()).isEqualTo(String.class);
        var mapping = (SqlAttributeMapping.SingleColumn) attribute.sql();
        assertThat(mapping.singleValueFromAttribute(new BigDecimal("42"))).isEqualTo("42");
        assertThat(mapping.columnValues(definition.sql().pathAlias("o"), "42").getFirst().value())
                .isEqualTo(new BigDecimal("42"));
    }
}
