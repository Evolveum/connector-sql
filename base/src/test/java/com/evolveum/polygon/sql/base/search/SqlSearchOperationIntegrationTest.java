/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.conndev.spi.BatchAwareResultHandler;
import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SQL search operation using H2 embedded database in MySQL mode.
 * Verifies QueryDSL-based search works correctly with H2's case-sensitivity handling.
 */
@Test(singleThreaded = true)
public class SqlSearchOperationIntegrationTest
        extends SqlIntegrationTestBase<SqlSearchOperationIntegrationTest.TestSqlConnector> {

    protected static class TestSqlConnector extends DefaultTestConnector {
        protected TestSqlConnector() { super(); }
    }

    @Override
    protected String[] resourceSchemaPaths() {
        return new String[]{"h2/basic/search_schema.sql", "h2/basic/search_data.sql"};
    }

    @Override
    protected void initConnector() {
        connector = new TestSqlConnector();
        connector.init(defaultConfig());
    }

    @Test
    public void testSchemaContainsDiscoveredObjectClasses() {
        assertThat(schemaNames()).contains(
                "app_user", "app_group", "app_role", "project", "useraddress", "projectmembership");
    }

    @Test
    public void testSearchWithUnqualifiedPaths() throws Exception {
        List<ConnectorObject> results = search("app_user", null);
        assertThat(results).hasSize(2);
        for (ConnectorObject o : results) {
            assertThat(o.getUid().getValue()).isNotNull();
            assertThat(o.getName()).isNotNull();
        }
    }

    @Test
    public void testSearchGroups() throws Exception {
        assertThat(search("app_group", null)).hasSize(2);
    }

    @Test
    public void testSearchRoles() throws Exception {
        assertThat(search("app_role", null)).hasSize(3);
    }

    @Test
    public void testSearchProjects() throws Exception {
        assertThat(search("project", null)).hasSize(2);
    }

    @Test
    public void testAllObjectClassesWork() throws Exception {
        for (String name : List.of("app_user", "app_group", "app_role", "project", "useraddress", "projectmembership")) {
            assertThat(search(name, null))
                    .isNotEmpty().withFailMessage("No results for " + name);
        }
    }

    @Test
    public void testSearchFlushesBatchAwareResultHandler() {
        var objectClass = connector.context().schema().objectClass(new ObjectClass("app_user"));
        var delivered = new ArrayList<ConnectorObject>();
        var pending = new ArrayList<ConnectorObject>();
        var handler = new BatchAwareResultHandler() {
            @Override
            public boolean handle(ConnectorObject object) {
                pending.add(object);
                return true;
            }

            @Override
            public void batchFinished() {
                delivered.addAll(pending);
                pending.clear();
            }
        };

        new SqlSearchExecutor(connector.context(), objectClass)
                .execute(null, handler, opts());

        assertThat(delivered).hasSize(2);
        assertThat(pending).isEmpty();
    }
}
