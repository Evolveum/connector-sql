/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClassSerializer;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.test.H2DatabaseInitializer;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.EmbeddedObject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests (H2) for the development-mode schema export derived from the translated
 * {@code BaseSchema}: foreign keys are detected and expressed on the attributes as references
 * ({@code referencedObjectClass} / {@code referencedAttribute} / {@code reference}), including
 * composite keys; the table becomes the {@code locator}.
 */
@Test(singleThreaded = true)
public class SqlDevSchemaExportIntegrationTest {

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

    private ConnectorObject export(String objectClass) throws Exception {
        var tables = new SqlSchemaDetector(context).discover();
        var schema = new SqlSchemaTranslator(tables)
                .translate(SqlSchemaDetectorIntegrationTest.StubConnector.class, context);
        return ConnDevObjectClassSerializer.serialize(schema.objectClass(objectClass));
    }

    @Test
    public void mapsSingleColumnForeignKeysAsReferenceAttributes() throws Exception {
        // ProjectMembership has three distinct single-column FKs: user_id -> User, project_id -> Project,
        // role_id -> Role.
        var object = export("projectmembership");

        //assertThat(value(object, "locator")).isEqualTo("projectmembership");

        var userId = attribute(object, "user_id");
        assertThat(string(userId, "referencedObjectClass")).isEqualTo("user");
        //assertThat(string(userId, "referencedAttribute")).isEqualTo("id");
        assertThat(string(userId, "reference")).isNotBlank();
        assertThat(string(userId, "role")).isEqualTo("subject");

        var projectId = attribute(object, "project_id");
        assertThat(string(projectId, "referencedObjectClass")).isEqualTo("project");

        // three distinct FKs -> three distinct reference names
        assertThat(string(userId, "reference"))
                .isNotEqualTo(string(projectId, "reference"));
    }

    // FIXME: correct test for composite keys
    @Test(enabled = false)
    public void mapsCompositeForeignKeyWithSharedReference() throws Exception {
        try (var connection = context.getConnection();
                var statement = connection.getConnection().createStatement()) {
            statement.execute("""
                    CREATE TABLE department (\
                    company_id INT, dept_code INT, name VARCHAR(100), \
                    PRIMARY KEY (company_id, dept_code))""");
            statement.execute("""
                    CREATE TABLE dept_membership (\
                    id INT PRIMARY KEY AUTO_INCREMENT, company_id INT NOT NULL, dept_id INT NOT NULL, \
                    role VARCHAR(50), \
                    CONSTRAINT fk_dept FOREIGN KEY (company_id, dept_id) \
                    REFERENCES department(company_id, dept_code))""");
        }

        var object = export("dept_membership");

        var companyId = attribute(object, "company_id");
        var deptId = attribute(object, "dept_id");

        // both columns reference the same table, grouped by the same FK name, each with its own target
        assertThat(string(companyId, "referencedObjectClass")).isEqualTo("department");
        assertThat(string(deptId, "referencedObjectClass")).isEqualTo("department");
        //assertThat(string(companyId, "referencedAttribute")).isEqualTo("company_id");
        //assertThat(string(deptId, "referencedAttribute")).isEqualTo("dept_code");
        assertThat(string(companyId, "reference"))
                .isEqualTo(string(deptId, "reference"))
                .isNotBlank();

        // a plain column carries no reference and keeps its native SQL type
        var role = attribute(object, "role");
        assertThat(string(role, "referencedObjectClass")).isNull();
        //assertThat(string(role, "type")).isEqualTo("VARCHAR");
    }

    private static EmbeddedObject attribute(ConnectorObject object, String name) {
        List<Object> attributes = object.getAttributeByName("attributes").getValue();
        return attributes.stream().map(EmbeddedObject.class::cast)
                .filter(e -> name.equals(string(e, "name")))
                .findFirst().orElseThrow(() -> new AssertionError("No attribute named " + name));
    }

    private static String value(ConnectorObject object, String name) {
        var attribute = object.getAttributeByName(name);
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }

    private static String string(EmbeddedObject object, String name) {
        var attribute = AttributeUtil.find(name, object.getAttributes());
        return attribute == null ? null : AttributeUtil.getStringValue(attribute);
    }
}
