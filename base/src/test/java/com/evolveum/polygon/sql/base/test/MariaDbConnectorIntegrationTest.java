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

/** Integration tests for MariaDB development metadata and native SQL definitions. */
@Test(singleThreaded = true)
public class MariaDbConnectorIntegrationTest {

    private MariaDbDatabaseInitializer database;
    private TestMariaDbConnector connector;

    private static final class TestMariaDbConnector
            extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {

        private TestMariaDbConnector() {
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
        database = MariaDbDatabaseInitializer.create();
        database.init();

        var configuration = new SqlConnectorConfiguration();
        configuration.setJdbcUrl(MariaDbDatabaseInitializer.JDBC_URL);
        configuration.setUsername(MariaDbDatabaseInitializer.USERNAME);
        configuration.setPassword(new GuardedString(
                MariaDbDatabaseInitializer.PASSWORD.toCharArray()));
        configuration.setPoolSize(5);
        configuration.setConnectionTimeout(10000);
        configuration.setValidateConnectionOnBorrow(true);
        configuration.setScanTables(true);
        configuration.setScanViews(true);
        configuration.setDevelopmentMode(true);

        connector = new TestMariaDbConnector();
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
    public void exportsMariaDbTableMetadataAndNativeDefinition() throws Exception {
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

        assertThat(attributeValue(appUser, SqlDevelopmentMode.CATALOG_ATTRIBUTE))
                .isEqualTo("connector_sql");
        assertThat(attributeValue(appUser, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE))
                .isEqualTo("TABLE");
        assertThat(attributeValue(appUser, SqlDevelopmentMode.REMARKS_ATTRIBUTE))
                .isEqualTo("Application users");
        assertThat(definition)
                .contains("CREATE TABLE `app_user`")
                .contains("`username` varchar(50) NOT NULL DEFAULT 'anonymous'")
                .contains("PRIMARY KEY (`id`)");
        assertThat(appUserContent)
                .contains("\"name\" : \"id\"")
                .contains("\"primaryKey\" : true")
                .contains("\"autoIncrement\" : true")
                .contains("\"defaultValue\" : \"'anonymous'\"")
                .contains("\"remarks\" : \"Application login name\"");
        assertThat(membershipContent)
                .contains("\"referencedTable\" : \"app_user\"")
                .contains("\"referencedColumn\" : \"id\"")
                .contains("\"foreignKeyName\" : \"fk_membership_user\"");
    }

    @Test
    public void exportsMariaDbViewDefinition() throws Exception {
        var view = tableNamed(search(SqlDevelopmentMode.TABLE_OC_NAME), "app_user_view");

        assertThat(attributeValue(view, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE)).isEqualTo("VIEW");
        assertThat((String) attributeValue(view, SqlDevelopmentMode.DEFINITION_ATTRIBUTE))
                .contains("VIEW `app_user_view` AS")
                .containsIgnoringCase("FROM `app_user`");
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
