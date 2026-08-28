/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests for SQLite development metadata and native SQL definitions. */
@Test(singleThreaded = true)
public class SqliteConnectorIntegrationTest {

    private SqliteDatabaseInitializer database;
    private TestSqliteConnector connector;

    private static final class TestSqliteConnector
            extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {

        private TestSqliteConnector() {
            super(false);
        }

        @Override
        protected void initializeObjectClassHandler(SqlHandlerLoader builder) {
        }

        @Override
        protected void initializeSchema(SqlSchemaDefinitionLoader loader) {
        }
    }

    @BeforeMethod
    public void setUp() throws Exception {
        database = SqliteDatabaseInitializer.create();
        database.init();

        var configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(database.jdbcUrl());
        configuration.setUsername("sqlite");
        configuration.setPassword(new GuardedString(new char[0]));
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(10000);
        configuration.setValidateConnectionOnBorrow(true);
        configuration.setScanTables(true);
        configuration.setScanViews(true);
        configuration.setDevelopmentMode(true);

        connector = new TestSqliteConnector();
        connector.init(configuration);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        if (connector != null) {
            connector.dispose();
            connector = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    @Test
    public void exportsSqliteTableMetadataAndNativeDefinition() throws Exception {
        assertThat(connector.schema().getObjectClassInfo().stream()
                .map(info -> info.getType()))
                .contains(SqlDevelopmentMode.TABLE_OC_NAME);

        var tables = search(SqlDevelopmentMode.TABLE_OC_NAME);
        var appUser = tableNamed(tables, "app_user");
        var membership = tableNamed(tables, "membership");
        var definition = (String) attributeValue(appUser, SqlDevelopmentMode.DEFINITION_ATTRIBUTE);
        var appUserContent = (String) attributeValue(
                appUser, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE);
        var membershipContent = (String) attributeValue(
                membership, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE);

        assertThat(attributeValue(appUser, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE))
                .isEqualTo("TABLE");
        assertThat(definition)
                .contains("CREATE TABLE app_user")
                .contains("id INTEGER PRIMARY KEY AUTOINCREMENT")
                .contains("username TEXT NOT NULL DEFAULT 'anonymous'");
        assertThat(appUserContent)
                .contains("\"name\" : \"id\"")
                .contains("\"primaryKey\" : true")
                .contains("\"autoIncrement\" : true")
                .contains("\"defaultValue\" : \"'anonymous'\"");
        assertThat(membershipContent)
                .contains("\"referencedTable\" : \"app_user\"")
                .contains("\"referencedColumn\" : \"id\"");
    }

    @Test
    public void exportsSqliteViewDefinition() throws Exception {
        var view = tableNamed(search(SqlDevelopmentMode.TABLE_OC_NAME), "app_user_view");

        assertThat(attributeValue(view, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE)).isEqualTo("VIEW");
        assertThat((String) attributeValue(view, SqlDevelopmentMode.DEFINITION_ATTRIBUTE))
                .contains("CREATE VIEW app_user_view AS")
                .contains("FROM app_user");
    }

    private List<ConnectorObject> search(String objectClass) throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass(objectClass), null, results::add,
                new OperationOptions(Collections.emptyMap()));
        return results;
    }

    private static ConnectorObject tableNamed(List<ConnectorObject> tables, String name) {
        return tables.stream()
                .filter(table -> table.getName().getNameValue().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow();
    }

    private static Object attributeValue(ConnectorObject object, String name) {
        return AttributeUtil.getSingleValue(object.getAttributeByName(name));
    }
}
