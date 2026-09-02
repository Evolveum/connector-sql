/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct database verification that parent deletion does not leave related rows behind. */
@Test(singleThreaded = true)
public class SqlRelatedTableCleanupIntegrationTest
        extends SqlIntegrationTestBase<SqlRelatedTableCleanupIntegrationTest.TestSqlConnector> {

    protected static class TestSqlConnector extends DefaultTestConnector {
        protected TestSqlConnector() {
            super();
        }
    }

    @Override
    protected String[] resourceSchemaPaths() {
        return new String[]{"h2/child-tables/schema.sql", "h2/child-tables/data.sql"};
    }

    @Override
    protected SqlConnectorConfiguration buildConfiguration() {
        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(url);
        config.setUsername("sa");
        config.setPassword(new GuardedString("".toCharArray()));
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(false);
        config.setDevelopmentMode(false);
        return config;
    }

    @Override
    protected void initConnector() {
        connector = new TestSqlConnector();
        connector.init(defaultConfig());
    }

    @Test
    public void deleteRemovesOwnedAndJunctionRows() throws Exception {
        var uid = connector.create(usersClass(), Set.of(
                AttributeBuilder.build(Name.NAME, "cleanup-user"),
                AttributeBuilder.build("username", "cleanup-user"),
                AttributeBuilder.build(childAttribute("user_emails"), "cleanup@example.com")), opts());
        execute("INSERT INTO user_group_membership (user_id, group_id) VALUES (?, 1)", uid.getUidValue());

        connector.delete(usersClass(), uid, opts());

        assertThat(count("SELECT COUNT(*) FROM users WHERE id = ?", uid.getUidValue())).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_emails WHERE user_id = ?", uid.getUidValue())).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_group_membership WHERE user_id = ?", uid.getUidValue())).isZero();
    }

    private ObjectClass usersClass() {
        return new ObjectClass(findOC("users").getType());
    }

    private String childAttribute(String tableName) {
        return findOC("users").getAttributeInfo().stream()
                .map(info -> info.getName())
                .filter(name -> name.equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow();
    }

    private int count(String sql, Object parameter) throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void execute(String sql, Object parameter) throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parameter);
            statement.executeUpdate();
        }
    }
}
