/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.build.api.SqlSchema;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlMappingDocumentation;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.test.H2DatabaseInitializer;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Work package #12486 — detected (auto-mapped) object classes and attributes carry documentation
 * (description) showing the table/column mapping and the native column type. The documentation is
 * set with the DETECTED origin ({@code DefinitionValue.detected}), so a description explicitly
 * declared in a connector script keeps precedence.
 */
@Test(singleThreaded = true)
public class SqlMappingDescriptionTest {

    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() {
        context = H2DatabaseInitializer.create();
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    private SqlSchema translated() throws Exception {
        var translator = new SqlSchemaTranslator(new SqlSchemaDetector(context).discover());
        var builder = translator.translate(List.of());
        translator.applyRules();
        builder.applyStructuralRules();
        return builder.build();
    }

    @Test
    public void detectedObjectClassDescriptionNamesTheMappedTable() throws Exception {
        var tables = new SqlSchemaDetector(context).discover();
        var table = table(tables, "project");
        var schema = translated();

        var oci = objectClass(schema, table.getName());
        assertThat(oci.getDescription())
                .isEqualTo(SqlMappingDocumentation.objectClass(table))
                .contains(table.getName());
    }

    @Test
    public void detectedAttributeDescriptionNamesTheColumnAndNativeType() throws Exception {
        var tables = new SqlSchemaDetector(context).discover();
        var table = table(tables, "project");
        var nameColumn = column(table, "NAME");
        var schema = translated();

        var attr = attribute(schema, table.getName(), "NAME");
        assertThat(attr.getDescription())
                .isEqualTo(SqlMappingDocumentation.attribute(table, nameColumn))
                .startsWith("Mapped to column \"NAME\"")
                .contains(table.getName());
        assertThat(attr.getDescription()).contains("native type");
    }

    @Test
    public void uidAndDerivedNameCarryTheKeyColumnDescription() throws Exception {
        var tables = new SqlSchemaDetector(context).discover();
        var table = table(tables, "project");
        var idColumn = column(table, "ID");
        var schema = translated();

        var expected = SqlMappingDocumentation.attribute(table, idColumn);
        assertThat(attribute(schema, table.getName(), Uid.NAME).getDescription()).isEqualTo(expected);
        // __NAME__ is derived from the UID mapping (same key column), so it carries the same documentation
        assertThat(attribute(schema, table.getName(), Name.NAME).getDescription()).isEqualTo(expected);
    }

    @Test
    public void compositeKeyUidDescriptionListsAllKeyColumns() throws Exception {
        try (var connection = context.getConnection();
             var statement = connection.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE department (
                        company_id INT, dept_code INT, name VARCHAR(100),
                        PRIMARY KEY (company_id, dept_code))""");
        }
        var tables = new SqlSchemaDetector(context).discover();
        var table = table(tables, "department");
        var mainColumn = column(table, "COMPANY_ID");
        var additionalColumn = column(table, "DEPT_CODE");
        var schema = translated();

        var uid = attribute(schema, table.getName(), Uid.NAME);
        assertThat(uid.getDescription())
                .isEqualTo(SqlMappingDocumentation.compositeKey(table.getName(), mainColumn, List.of(additionalColumn)))
                .containsIgnoringCase("company_id")
                .containsIgnoringCase("dept_code")
                .contains("composite key");
    }

    @Test
    public void declaredDescriptionTakesPrecedenceOverDetected() throws Exception {
        var tables = new SqlSchemaDetector(context).discover();
        var table = table(tables, "project");

        var builder = new SqlSchemaBuilderImpl(SqlSchemaDetectorIntegrationTest.StubConnector.class, null);
        var oc = builder.objectClass(table.getName());
        oc.description("Declared object class description");
        oc.attribute("NAME").connId().description("Declared attribute description");

        var translator = new SqlSchemaTranslator(builder, tables);
        var populated = translator.translate(List.of());
        translator.applyRules();
        populated.applyStructuralRules();
        var schema = populated.build();

        assertThat(objectClass(schema, table.getName()).getDescription())
                .isEqualTo("Declared object class description");
        assertThat(attribute(schema, table.getName(), "NAME").getDescription())
                .isEqualTo("Declared attribute description");
        // attributes without a declared description still get the detected documentation
        assertThat(attribute(schema, table.getName(), "CREATED_AT").getDescription())
                .isEqualTo(SqlMappingDocumentation.attribute(table, column(table, "CREATED_AT")));
    }

    // --- Helpers ---

    private static SqlTableInfo table(List<SqlTableInfo> tables, String name) {
        return tables.stream()
                .filter(t -> name.equalsIgnoreCase(t.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Table not found: " + name));
    }

    private static SqlColumnMeta column(SqlTableInfo table, String name) {
        return table.getColumns().stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Column not found: " + name + " in " + table.getName()));
    }

    private static ObjectClassInfo objectClass(SqlSchema schema, String name) {
        return schema.connIdSchema().getObjectClassInfo().stream()
                .filter(oci -> oci.getType().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Object class not found: " + name));
    }

    private static AttributeInfo attribute(SqlSchema schema, String objectClass, String name) {
        return objectClass(schema, objectClass).getAttributeInfo().stream()
                .filter(a -> a.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Attribute not found: " + name + " in " + objectClass));
    }
}
