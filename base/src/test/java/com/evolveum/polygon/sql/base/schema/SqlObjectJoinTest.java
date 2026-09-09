/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlJoinBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SqlObjectJoinTest {
    @Test
    public void implementsTheStorySyntaxWithoutReplacingRootIdentifiers() {
        var definition = schema("""
                sql {
                    table 'organization'
                    join {
                        table 'organization_i18n'
                        prefixAttributes 'en_'
                        skipAttributes 'org_id'
                        where { q -> q.column('lang').eq('en') }
                    }
                    join {
                        table 'organization_i18n'
                        prefixAttributes 'fr_'
                        skipAttributes 'org_id'
                        on { j -> j.left().column('id').eq(j.right().column('org_id')) }
                        where { q -> q.column('lang').eq('fr') }
                    }
                }
                attribute('en_title') { connId { name 'englishTitle' } }
                attribute('en_row_id') { connId { type String } }
                readOnly false
                """, tables());
        assertThat(definition.getReadOnly()).isTrue();
        assertThat(definition.sql().joins()).hasSize(2);
        assertThat(definition.sql().joins().getFirst().condition().toString())
                .contains("o.id = j1.org_id", "j1.lang = en");
        assertThat(definition.sql().joins().getLast().condition().toString())
                .contains("o.id = j2.org_id", "j2.lang = fr");
        assertThat(definition.attributeFromConnIdName("en_org_id")).isNull();
        assertThat(definition.attributeFromConnIdName("englishTitle").sql().column().value()).isEqualTo("title");
        assertThat(definition.attributeFromConnIdName(Uid.NAME).sql().column().value()).isEqualTo("id");
        assertThat(definition.attributeFromConnIdName(Name.NAME).sql().column().value()).isEqualTo("id");
        var number = (SqlAttributeMapping.SingleColumn) definition.attributeFromConnIdName("en_row_id").sql();
        assertThat(number.dslPath(definition.sql().pathAlias("o")).toString()).isEqualTo("j1.row_id");
        assertThat(number.singleValueFromAttribute(42)).isEqualTo("42");
        assertThat(number.sqlFilter().eq(definition.sql().pathAlias("o"), "42").toString()).isEqualTo("j1.row_id = 42");
        assertThat(definition.attributes()).allSatisfy(attribute -> {
            assertThat(attribute.connId().isCreateable()).isFalse();
            assertThat(attribute.connId().isUpdateable()).isFalse();
        });
    }

    @DataProvider
    public Object[][] invalidJoins() {
        return new Object[][] {
                { "join { prefixAttributes 'en_' }", "requires a table" },
                { "join { table 'missing'; prefixAttributes 'en_' }", "exactly one detected" },
                { "join { table 'organization_i18n'; skipAttributes 'typo' }", "Unknown skipped column" },
                { "join { table 'organization_i18n'; prefixAttributes 'en_'; on { j -> } }", "at least one" },
                { "join { table 'organization_i18n'; prefixAttributes 'en_'; on { j -> j.left().column('missing').eq(j.right().column('org_id')) } }", "Column missing" },
                { "join { table 'organization_i18n'; prefixAttributes 'en_'; on { j -> j.left().column('id').eq(j.left().column('id')) } }", "left and right" },
                { "join { table 'organization_i18n'; prefixAttributes 'en_'; where { q -> q.column('missing').eq('en') } }", "Column not found" },
                { "join { table 'organization_i18n'; prefixAttributes 'en_' }; join { table 'organization_i18n'; prefixAttributes 'en_' }", "Duplicate" }
        };
    }

    @Test(dataProvider = "invalidJoins")
    public void rejectsInvalidJoinConfigurations(String join, String message) {
        assertThatThrownBy(() -> schema("sql { table 'organization'; " + join + " }", tables()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }

    @Test
    public void rejectsCollidingConnIdRenamesAndJoinedUid() {
        var join = "sql { table 'organization'; join { table 'organization_i18n'; prefixAttributes 'en_' } }";
        assertThatThrownBy(() -> schema(join + "\nattribute('en_title') { connId { name 'title' } }", tables()))
                .hasMessageContaining("Duplicate joined object attribute");
        assertThatThrownBy(() -> schema(join + "\nattribute('en_row_id') { connId { name '__UID__' } }", tables()))
                .hasMessageContaining("cannot replace the root UID");
    }

    @Test
    public void requiresExplicitOnForViewsOrAmbiguousForeignKeys() {
        var tables = tables();
        tables.getLast().getColumns().stream().filter(column -> column.getName().equals("org_id"))
                .forEach(column -> column.setForeignKey(null, null, null));
        var inferred = "sql { table 'organization'; join { table 'organization_i18n'; prefixAttributes 'en_' } }";
        assertThatThrownBy(() -> schema(inferred, tables)).hasMessageContaining("unambiguous foreign key");
        assertThat(schema("""
                sql { table 'organization'; join {
                    table 'organization_i18n'; prefixAttributes 'en_'
                    on { j -> j.left().column('id').eq(j.right().column('org_id')) }
                } }
                """, tables).sql().joins()).hasSize(1);

        var ambiguous = tables();
        ambiguous.getLast().getColumns().stream().filter(column -> column.getName().equals("row_id"))
                .forEach(column -> column.setForeignKey(null, "public", "organization", "id", "fk_other"));
        assertThatThrownBy(() -> schema(inferred, ambiguous)).hasMessageContaining("unambiguous foreign key");
    }

    @Test
    public void includesJoinedTablesInTargetedDiscovery() {
        var builder = builder();
        var loader = new SqlSchemaDefinitionLoader(builder, new SqlConnectorConfiguration().groovyContext());
        loader.load("""
                objectClass('Organization') { sql {
                    table 'organization'
                    join { table 'organization_i18n'; prefixAttributes 'en_' }
                    join { table 'organization_i18n'; prefixAttributes 'fr_' }
                } }
                """);
        assertThat(builder.tableRefs()).extracting(SqlSchemaDetector.TableRef::table)
                .containsExactly("organization", "organization_i18n");
    }

    @Test
    public void infersForeignKeysInTheRootToJoinedTableDirection() {
        var tables = tables();
        var join = new SqlJoinBuilder();
        join.table("organization");
        assertThat(join.resolve(tables.getLast(), tables, 1).condition().toString())
                .isEqualTo("o.org_id = j1.id");
    }

    @DataProvider
    public Object[][] mismatchedForeignKeyTargets() {
        return new Object[][] {
                { null, "archive" },
                { "other_database", "public" },
                { null, null }
        };
    }

    @Test(dataProvider = "mismatchedForeignKeyTargets")
    public void requiresExplicitOnWhenForeignKeyTargetIsDifferentOrUnknown(String catalog, String schema) {
        var tables = tables();
        tables.getLast().getColumns().stream().filter(column -> column.getName().equals("org_id"))
                .forEach(column -> column.setForeignKey(catalog, schema, "organization", "id", "fk_org"));
        var join = new SqlJoinBuilder();
        join.table("organization_i18n");
        assertThatThrownBy(() -> join.resolve(tables.getFirst(), tables, 1))
                .hasMessageContaining("unambiguous foreign key");
        join.table("organization");
        assertThatThrownBy(() -> join.resolve(tables.getLast(), tables, 1))
                .hasMessageContaining("unambiguous foreign key");
    }

    @Test
    public void requiresExplicitDirectionForSelfJoins() {
        assertThatThrownBy(() -> schema("sql { table 'organization'; join { table 'organization'; prefixAttributes 'copy_' } }", tables()))
                .hasMessageContaining("Self-join requires explicit on");
        var definition = schema("""
                sql { table 'organization'; join {
                    table 'organization'; prefixAttributes 'copy_'
                    on { j -> j.left().column('id').eq(j.right().column('id')) }
                } }
                """, tables());
        assertThat(definition.sql().joins().getFirst().condition().toString()).isEqualTo("o.id = j1.id");
    }

    @Test
    public void includesOnlyExplicitlyListedJoinedAttributes() {
        var definition = schema("""
                sql { table 'organization'; join { table 'organization_i18n'; prefixAttributes 'en_' } }
                attribute('id') { connId { name '__UID__' } }
                attribute('en_title') { connId { name 'englishTitle' } }
                """, tables(), true);
        assertThat(definition.attributes()).extracting(attribute -> attribute.connId().getName())
                .containsExactlyInAnyOrder(Uid.NAME, Name.NAME, "englishTitle");
        var title = (SqlAttributeMapping.SingleColumn) definition.attributeFromConnIdName("englishTitle").sql();
        assertThat(title.dslPath(definition.sql().pathAlias("o")).toString()).isEqualTo("j1.title");
    }

    @Test
    public void discoversJoinedAttributesWhenNoAttributeListIsDeclared() {
        var definition = schema("""
                sql { table 'organization'; join { table 'organization_i18n'; prefixAttributes 'en_' } }
                """, tables(), true);
        assertThat(definition.attributes()).extracting(attribute -> attribute.connId().getName())
                .containsExactlyInAnyOrder(Uid.NAME, Name.NAME, "title", "en_row_id", "en_org_id", "en_lang", "en_title");
    }

    @Test
    public void rejectsMissingRootAfterDiscovery() {
        assertThatThrownBy(() -> schema("""
                sql { table 'missing'; join { table 'organization_i18n'; prefixAttributes 'en_' } }
                attribute('id') { connId { name '__UID__'; type String } }
                """, List.of())).hasMessageContaining("Root table was not detected");
    }

    private SqlObjectClassDefinition schema(String body, List<SqlTableInfo> tables) {
        return schema(body, tables, false);
    }

    private SqlObjectClassDefinition schema(String body, List<SqlTableInfo> tables, boolean explicitlyListed) {
        var builder = builder();
        builder.onlyExplicitlyListed(explicitlyListed);
        var loader = new SqlSchemaDefinitionLoader(builder, new SqlConnectorConfiguration().groovyContext());
        loader.load("objectClass('Organization') { schema 'public'; " + body + " }");
        var translator = new SqlSchemaTranslator(builder, tables);
        translator.translate(List.of());
        translator.applyRules();
        builder.applyStructuralRules();
        return builder.build().objectClass(new ObjectClass("Organization"));
    }

    private SqlSchemaBuilderImpl builder() {
        var builder = new SqlSchemaBuilderImpl(ManifestBasedConnector.class,
                new SqlBaseContext(new SqlConnectorConfiguration()));
        return builder;
    }

    private List<SqlTableInfo> tables() {
        var foreignKey = column("org_id", SqlSchemaValueMapping.INTEGER, false);
        foreignKey.setForeignKey(null, "public", "organization", "id", "fk_org");
        return List.of(
                SqlTableInfo.builder().name("organization").schema("public").tableType("TABLE")
                        .columns(List.of(column("id", SqlSchemaValueMapping.INTEGER, true),
                                column("title", SqlSchemaValueMapping.VARCHAR, false))).build(),
                SqlTableInfo.builder().name("organization_i18n").schema("public").tableType("TABLE")
                        .columns(List.of(column("row_id", SqlSchemaValueMapping.INTEGER, true), foreignKey,
                                column("lang", SqlSchemaValueMapping.VARCHAR, false),
                                column("title", SqlSchemaValueMapping.VARCHAR, false))).build());
    }

    private SqlColumnMeta column(String name, SqlSchemaValueMapping mapping, boolean primaryKey) {
        return SqlColumnMeta.builder().name(name).valueMapping(mapping).javaType(mapping.primaryWireType())
                .primaryKey(primaryKey).nullable(false).build();
    }
}
