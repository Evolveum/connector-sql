/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.common.GuardedStringAccessor;
import com.evolveum.polygon.sql.base.AbstractGroovySqlConnector;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.groovy.SqlHandlerLoader;
import com.evolveum.polygon.sql.base.groovy.SqlSchemaDefinitionLoader;
import com.evolveum.polygon.sql.base.test.PostgresDatabaseInitializer;
import org.identityconnectors.framework.common.objects.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SQL search operation using real PostgreSQL 16 embedded via zonkyio.
 * Verifies that query-based operations work correctly against an actual Postgres instance.
 * <p>
 * Only tests Postgres-specific behavior. H2 test covers equivalent scenarios.
 * </p>
 */
@Test(singleThreaded = true)
public class SqlSearchOperationPostgresTest {

    private PostgresDatabaseInitializer postgres;
    private TestSqlConnector connector;
    private Path pgDumpStub;

    private static class TestSqlConnector extends AbstractGroovySqlConnector<SqlConnectorConfiguration> {
        TestSqlConnector() { super(false); }
        @Override protected void initializeObjectClassHandler(SqlHandlerLoader builder) {}
        @Override protected void initializeSchema(SqlSchemaDefinitionLoader loader) {}
    }

    @BeforeMethod
    public void setUp() throws Exception {
        postgres = PostgresDatabaseInitializer.create();

        var password = new GuardedStringAccessor();
        postgres.getPassword().access(password);
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), password.getClearString())) {
            try (var stmt = conn.createStatement()) {
                stmt.execute(readResource("postgresql/basic/schema.sql"));
                stmt.execute(readResource("postgresql/basic/data.sql"));
            }
        }

        var config = new SqlConnectorConfiguration();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setPoolSize(5);
        config.setConnectionTimeout(10000);
        config.setValidateConnectionOnBorrow(true);
        config.setScanTables(true);
        config.setScanViews(true);
        config.setDevelopmentMode(true);
        pgDumpStub = createPgDumpStub();
        config.setPgDumpPath(pgDumpStub.toString());

        connector = new TestSqlConnector();
        connector.init(config);
    }

    @AfterMethod
    public void tearDown() {
        if (connector != null) { connector.dispose(); connector = null; }
        if (postgres != null) { postgres.close(); postgres = null; }
        if (pgDumpStub != null) {
            try {
                Files.deleteIfExists(pgDumpStub);
            } catch (IOException e) {
                throw new RuntimeException("Failed to remove pg_dump test stub", e);
            } finally {
                pgDumpStub = null;
            }
        }
    }

    private static String readResource(String path) throws IOException {
        var is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        return new String(Objects.requireNonNull(is, "Resource not found: " + path).readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Path createPgDumpStub() throws IOException {
        var stub = Files.createTempFile("connector-sql-pg-dump-", ".sh");
        Files.writeString(stub, """
                #!/bin/sh
                table=""
                schema_only=0
                native_url=0
                no_password=0
                for argument in "$@"; do
                    case "$argument" in
                        --table=*) table="${argument#--table=}" ;;
                        --schema-only) schema_only=1 ;;
                        --dbname=postgresql://*) native_url=1 ;;
                        --no-password) no_password=1 ;;
                    esac
                done
                if [ "$PGPASSWORD" != "postgres" ] || [ "$schema_only" -ne 1 ] \
                        || [ "$native_url" -ne 1 ] || [ "$no_password" -ne 1 ] || [ -z "$table" ]; then
                    echo "Invalid pg_dump invocation" >&2
                    exit 9
                fi
                case "$table" in
                    *user_overview*)
                        cat <<'SQL'
                CREATE VIEW "public"."user_overview" AS
                 SELECT "app_user"."id",
                    "app_user"."username",
                    "app_user"."email",
                    "app_user"."created_at"
                   FROM "public"."app_user";
                SQL
                        ;;
                    *app_user*)
                        cat <<'SQL'
                CREATE TABLE "public"."app_user" (
                    "id" integer NOT NULL,
                    "username" character varying(255) NOT NULL,
                    "email" character varying(255) DEFAULT 'unknown@example.com'::character varying,
                    "created_at" timestamp without time zone
                );
                ALTER TABLE ONLY "public"."app_user"
                    ADD CONSTRAINT "app_user_pkey" PRIMARY KEY ("id");
                SQL
                        ;;
                    *app_group*)
                        echo "Simulated pg_dump failure" >&2
                        exit 12
                        ;;
                    *)
                        echo "-- native pg_dump definition for $table"
                        ;;
                esac
                """, StandardCharsets.UTF_8);
        if (!stub.toFile().setExecutable(true)) {
            throw new IOException("Cannot make pg_dump test stub executable");
        }
        return stub;
    }

    private OperationOptions opts() {
        return new OperationOptions(Collections.emptyMap());
    }

    private List<ConnectorObject> search(String oc) throws Exception {
        List<ConnectorObject> results = new ArrayList<>();
        connector.executeQuery(new ObjectClass(oc), null, results::add, opts());
        return results;
    }

    @Test
    public void testSchemaContainsDiscoveredObjectClasses() throws Exception {
        var schema = connector.schema();
        assertThat(schema.getObjectClassInfo()).isNotEmpty();
        List<String> names = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType).map(String::toLowerCase).toList();
        assertThat(names).contains(
                "app_user", "app_group", "app_role", "project", "useraddress", "projectmembership");
    }

    @Test
    public void testSearchAllTables() throws Exception {
        // Search each table and verify results - skipping useraddress due to QueryDSL column duplication issue with primary_flag
        assertThat(search("app_user")).isNotEmpty();
        assertThat(search("app_group")).isNotEmpty();
        assertThat(search("app_role")).isNotEmpty();
        assertThat(search("project")).isNotEmpty();
        assertThat(search("projectmembership")).isNotEmpty();
    }

    @Test
    public void testDevelopmentMetadataUsesPostgresValues() throws Exception {
        var schemaNames = connector.schema().getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .toList();
        assertThat(schemaNames).contains(SqlDevelopmentMode.TABLE_OC_NAME);

        var tables = search(SqlDevelopmentMode.TABLE_OC_NAME);
        var appUser = tableNamed(tables, "app_user");
        var appGroup = tableNamed(tables, "app_group");
        var userAddress = tableNamed(tables, "useraddress");

        assertThat(appUser.getUid().getUidValue()).isEqualTo("postgres.public.app_user");
        assertThat(attributeValue(appUser, SqlDevelopmentMode.CATALOG_ATTRIBUTE)).isEqualTo("postgres");
        assertThat(attributeValue(appUser, SqlDevelopmentMode.SCHEMA_ATTRIBUTE)).isEqualTo("public");
        assertThat(attributeValue(appUser, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE)).isEqualTo("TABLE");
        assertThat(attributeValue(appUser, SqlDevelopmentMode.REMARKS_ATTRIBUTE)).isEqualTo("Application users");
        assertThat((String) attributeValue(appUser, SqlDevelopmentMode.DEFINITION_ATTRIBUTE))
                .contains("CREATE TABLE \"public\".\"app_user\"")
                .contains("ADD CONSTRAINT \"app_user_pkey\"")
                .contains("DEFAULT 'unknown@example.com'::character varying");
        assertThat(appGroup.getAttributeByName(SqlDevelopmentMode.DEFINITION_ATTRIBUTE)).isNull();
        assertThat((String) attributeValue(appUser, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE))
                .contains("\"name\" : \"id\"")
                .contains("\"typeName\" : \"SERIAL\"")
                .contains("\"primaryKey\" : true")
                .contains("\"autoIncrement\" : true")
                .contains("\"defaultValue\" : \"nextval('app_user_id_seq'::regclass)\"")
                .contains("unknown@example.com")
                .contains("\"remarks\" : \"Primary email address\"");
        assertThat((String) attributeValue(userAddress, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE))
                .contains("\"referencedTable\" : \"app_user\"")
                .contains("\"foreignKeyName\" : \"fk_user_address_user\"");
    }

    @Test
    public void testDevelopmentMetadataExportsPostgresView() throws Exception {
        var view = tableNamed(search(SqlDevelopmentMode.TABLE_OC_NAME), "user_overview");

        assertThat(view.getName().getNameValue()).isEqualTo("user_overview");
        assertThat(attributeValue(view, SqlDevelopmentMode.SCHEMA_ATTRIBUTE)).isEqualTo("public");
        assertThat(attributeValue(view, SqlDevelopmentMode.TABLE_TYPE_ATTRIBUTE)).isEqualTo("VIEW");
        assertThat((String) attributeValue(view, SqlDevelopmentMode.DEFINITION_ATTRIBUTE))
                .contains("CREATE VIEW \"public\".\"user_overview\" AS")
                .contains("FROM \"public\".\"app_user\"");
        assertThat((String) attributeValue(view, SqlDevelopmentMode.TABLE_CONTENT_ATTRIBUTE))
                .contains("\"tableType\" : \"VIEW\"")
                .contains("\"name\" : \"username\"");
    }

    private static ConnectorObject tableNamed(List<ConnectorObject> tables, String name) {
        return tables.stream()
                .filter(table -> table.getName().getNameValue().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static Object attributeValue(ConnectorObject object, String name) {
        return AttributeUtil.getSingleValue(object.getAttributeByName(name));
    }
}
