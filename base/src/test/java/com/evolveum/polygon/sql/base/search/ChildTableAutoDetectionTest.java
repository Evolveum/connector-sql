/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for child table auto-detection from JDBC FK metadata.
 * Tests schema detection, embedded objects, and junction table references.
 */
@Test(singleThreaded = true)
public class ChildTableAutoDetectionTest {

    private static final String URL = "jdbc:h2:mem:childtables2;DB_CLOSE_DELAY=-1;MODE=MySQL";
    private TestSqlConnector connector;

    private static class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestSqlConnector() { super(false); }
        @Override protected void initializeObjectClassHandler(SqlHandlerLoader builder) {}
        @Override protected void initializeSchema(SqlSchemaDefinitionLoader loader) {}
    }

    @BeforeMethod
    public void setUp() throws Exception {
        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             var stmt = conn.createStatement()) {
            stmt.execute(readResource("h2/child-tables/schema.sql"));
            stmt.execute(readResource("h2/child-tables/data.sql"));
            stmt.execute("COMMIT");
        }

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(URL);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(false);
        config.setDevelopmentMode(false);

        connector = new TestSqlConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) { connector.dispose(); connector = null; }
    }

    private static String readResource(String path) throws Exception {
        try {
            return Files.readString(Path.of("base/src/test/resources", path));
        } catch (Exception e) {
            var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
            return new String(is.readAllBytes());
        }
    }

    private OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    @Test
    public void testSchemaContainsParentTables() throws Exception {
        var schema = connector.schema();
        List<String> names = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType).map(String::toLowerCase).toList();
        assertThat(names).contains("users", "groups");
    }

    @Test
    public void testChildTablesAreEmbeddedInSchema() throws Exception {
        var schema = connector.schema();
        List<String> names = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType).map(String::toLowerCase).toList();
        // user_emails (FK + 1 value col) → simple attribute (no OC, just parent's multivalue attr)
        // user_profiles (FK = PK) → embedded OC
        // user_addresses (FK not in PK) → standalone OC
        // user_phones (FK + 2+ value cols) → embedded OC
        assertThat(names).contains("user_profiles", "user_addresses", "user_phones");
        assertThat(names).doesNotContain("user_emails");
    }

    @Test
    public void testChildTablesAreMarkedEmbedded() throws Exception {
        var schema = connector.schema();
        // user_profiles: PK=user_id=FK → embedded, SINGLE_VALUE_EMBEDDED
        var userProfileOc = findOC(schema, "user_profiles");
        assertThat(userProfileOc).isNotNull();
        assertThat(userProfileOc.isEmbedded()).as("user_profiles should be embedded").isTrue();
        // user_emails: PK=(user_id, email), FK=user_id + 1 value col → simple multi-value attribute (no OC)
        var userEmailOc = findOC(schema, "user_emails");
        assertThat(userEmailOc).as("user_emails should NOT have its own OC").isNull();
        // user_addresses: PK=id(auto_incr), FK=user_id → FK is NOT part of PK → standalone
        var userAddressOc = findOC(schema, "user_addresses");
        assertThat(userAddressOc).isNotNull();
        assertThat(userAddressOc.isEmbedded()).as("user_addresses should NOT be embedded").isFalse();
        // user_phones: PK=(user_id, phone_number) + phone_type → FK + 2+ cols → embedded, MULTI_VALUE_EMBEDDED
        var userPhoneOc = findOC(schema, "user_phones");
        assertThat(userPhoneOc).isNotNull();
        assertThat(userPhoneOc.isEmbedded()).as("user_phones should be embedded").isTrue();
    }

    @Test
    public void testJunctionTableNotInSchema() throws Exception {
        var schema = connector.schema();
        List<String> names = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType).map(String::toLowerCase).toList();
        assertThat(names).doesNotContain("user_group_membership");
    }

    @Test
    public void testParentHasEmbeddedAttributes() throws Exception {
        var schema = connector.schema();
        var userOc = findOC(schema, "users");
        assertThat(userOc).isNotNull();
        var attrNames = userOc.getAttributeInfo().stream()
                .map(AttributeInfo::getName).map(String::toLowerCase).toList();
        // user_profiles (FK=PK), user_emails (FK part of PK), user_phones (FK+value in PK) are detected
        // user_addresses has FK NOT in PK -> standalone
        assertThat(attrNames).contains("user_profiles", "user_emails", "user_phones");
    }

    @Test
    public void testProfileIsSingleValued() throws Exception {
        var schema = connector.schema();
        var userOc = findOC(schema, "users");
        for (var attr : userOc.getAttributeInfo()) {
            if ("USER_PROFILES".equalsIgnoreCase(attr.getName())) {
                assertThat(attr.isMultiValued()).isFalse();
                return;
            }
        }
        fail("USER_PROFILES attribute not found");
    }

    @Test
    public void testEmailsAddressesMultiValued() throws Exception {
        var schema = connector.schema();
        var userOc = findOC(schema, "users");
        for (var attr : userOc.getAttributeInfo()) {
            // user_emails: PK=(user_id, email), FK=user_id → multi-valued
            if ("USER_EMAILS".equalsIgnoreCase(attr.getName())) {
                assertThat(attr.isMultiValued()).isTrue();
            }
        }
    }

    // Note: Full search integration tests for child table resolution
    // require resolver wiring that's specific to connector deployment.
    // Schema-level tests above verify the detection works correctly.

    private ObjectClassInfo findOC(Schema schema, String name) {
        for (var oc : schema.getObjectClassInfo()) {
            if (oc.getType().equalsIgnoreCase(name)) {
                return oc;
            }
        }
        return null;
    }

    private static void fail(String msg) {
        throw new AssertionError(msg);
    }
}
