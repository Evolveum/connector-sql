package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.schema.strategy.DefaultDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.NamedColumnUidStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.NonPkColumnsOnlyStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.PrefixEmbeddedDetectionStrategy;
import com.evolveum.polygon.sql.base.test.H2DatabaseInitializer;
import org.identityconnectors.framework.common.objects.Uid;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Test(singleThreaded = true)
public class SqlSchemaTranslatorTest {

    private SqlBaseContext context;

    @BeforeMethod
    public void setUp() throws Exception {
        context = H2DatabaseInitializer.create();
    }

    @AfterMethod
    public void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    private BaseSchema translated(SqlSchemaTranslator translator) {
        return translator.translate(SqlSchemaDetectorIntegrationTest.StubConnector.class, context);
    }

    private List<SqlTableInfo> discovered() throws Exception {
        return new SqlSchemaDetector(context).discover();
    }

    @Test
    public void testDefaultStrategySinglePkUid() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(discovered())
                .addStrategy(new DefaultDetectionStrategy());

        assertThat(translated(translator).connIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testNamedColumnUidStrategy() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(discovered())
                .addStrategy(new NamedColumnUidStrategy("uid"));

        assertThat(translated(translator).connIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testNonPkColumnsOnlyStrategy() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(discovered())
                .addStrategy(new NonPkColumnsOnlyStrategy());

        assertThat(translated(translator).connIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testPrefixEmbeddedDetectionStrategy() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(discovered())
                .addStrategy(new PrefixEmbeddedDetectionStrategy("user"));

        assertThat(translated(translator).connIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testStrategyComposition() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(discovered())
                .addStrategy(new NamedColumnUidStrategy("id"))
                .addStrategy(new NonPkColumnsOnlyStrategy())
                .addStrategy(new PrefixEmbeddedDetectionStrategy("user"));

        assertThat(translated(translator).connIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testEmptySchema() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(List.of());

        // an empty model still yields the __Dummy workaround class required for test connection
        var objectClasses = translated(translator).connIdSchema().getObjectClassInfo();
        assertThat(objectClasses).hasSize(1);
        assertThat(objectClasses.iterator().next().getType()).isEqualTo("__Dummy");
    }

    @Test
    public void testNullSchema() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(null);

        var objectClasses = translated(translator).connIdSchema().getObjectClassInfo();
        assertThat(objectClasses).hasSize(1);
        assertThat(objectClasses.iterator().next().getType()).isEqualTo("__Dummy");
    }

    @Test
    public void testTranslatedModelKeepsNativeSide() throws Exception {
        BaseSchema schema = translated(new SqlSchemaTranslator(discovered()));

        var user = schema.objectClass("user");
        assertThat(user).isNotNull();
        assertThat(user.locator()).isEqualTo("user");

        // the single-PK "id" column maps to __UID__ but keeps its native name and SQL type
        var id = user.attributeFromProtocolName("id");
        assertThat(id.connId().getName()).isEqualTo(Uid.NAME);
        assertThat(id.nativeType()).isEqualTo("INT");

        // auto-increment identity cannot be written
        assertThat(id.connId().isCreateable()).isFalse();
        assertThat(id.connId().isUpdateable()).isFalse();
    }

    @Test
    public void testForeignKeyBecomesReference() throws Exception {
        BaseSchema schema = translated(new SqlSchemaTranslator(discovered()));

        var membership = schema.objectClass("projectmembership");
        var userId = membership.attributeFromProtocolName("user_id");
        assertThat(userId.connId().getReferencedObjectClassName()).isEqualTo("user");
        assertThat(userId.referencedAttribute()).isEqualTo("id");
        assertThat(userId.connId().getSubtype()).isNotBlank();
        assertThat(userId.connId().isRequired()).isTrue();
    }

    @Test
    public void testTableWithCompositePk() throws Exception {
        SqlTableInfo tableWithCompositePk = SqlTableInfo.builder()
                .name("composite_table")
                .addColumn(SqlColumnMeta.builder().name("col1").typeName("INT").primaryKey(true).build())
                .addColumn(SqlColumnMeta.builder().name("col2").typeName("INT").primaryKey(true).build())
                .addColumn(SqlColumnMeta.builder().name("value").typeName("VARCHAR").build())
                .build();

        List<SqlColumnMeta> columns = new DefaultDetectionStrategy().detectColumns(tableWithCompositePk);
        assertThat(columns).hasSize(3);

        Optional<SqlColumnMeta> uid = new DefaultDetectionStrategy().resolveUid(tableWithCompositePk);
        assertThat(uid).isEmpty();
    }

    @Test
    public void testTableWithSinglePk() throws Exception {
        SqlTableInfo table = SqlTableInfo.builder()
                .name("simple_table")
                .addColumn(SqlColumnMeta.builder().name("id").typeName("INT").primaryKey(true).autoIncrement(true).build())
                .addColumn(SqlColumnMeta.builder().name("name").typeName("VARCHAR").nullable(true).build())
                .build();

        List<SqlColumnMeta> columns = new DefaultDetectionStrategy().detectColumns(table);
        assertThat(columns).hasSize(2);

        Optional<SqlColumnMeta> uid = new DefaultDetectionStrategy().resolveUid(table);
        assertThat(uid).isPresent();
        assertThat(uid.get().getName()).isEqualTo("id");
    }

    @Test
    public void testTableWithNoPk() throws Exception {
        SqlTableInfo table = SqlTableInfo.builder()
                .name("no_pk_table")
                .addColumn(SqlColumnMeta.builder().name("field1").typeName("VARCHAR").build())
                .build();

        Optional<SqlColumnMeta> uid = new DefaultDetectionStrategy().resolveUid(table);
        assertThat(uid).isEmpty();
    }

    @Test
    public void testNamedColumnUidStrategyWithNoMatch() throws Exception {
        SqlTableInfo table = SqlTableInfo.builder()
                .name("test_table")
                .addColumn(SqlColumnMeta.builder().name("id").typeName("INT").primaryKey(true).build())
                .addColumn(SqlColumnMeta.builder().name("name").typeName("VARCHAR").build())
                .build();

        Optional<SqlColumnMeta> uid = new NamedColumnUidStrategy("nonexistent").resolveUid(table);
        assertThat(uid).isPresent();
        assertThat(uid.get().getName()).isEqualTo("id");
    }

    @Test
    public void testNonPkColumnsOnlyStrategyFiltersPks() throws Exception {
        SqlTableInfo table = SqlTableInfo.builder()
                .name("test_table")
                .addColumn(SqlColumnMeta.builder().name("id").typeName("INT").primaryKey(true).build())
                .addColumn(SqlColumnMeta.builder().name("name").typeName("VARCHAR").build())
                .addColumn(SqlColumnMeta.builder().name("email").typeName("VARCHAR").build())
                .build();

        NonPkColumnsOnlyStrategy strategy = new NonPkColumnsOnlyStrategy();
        List<SqlColumnMeta> columns = strategy.detectColumns(table);

        assertThat(columns).hasSize(2);
        assertThat(columns).extracting(SqlColumnMeta::getName)
                .containsOnly("name", "email");
    }

    @Test
    public void testIsEmbeddedDefault() throws Exception {
        SqlTableInfo table = SqlTableInfo.builder()
                .name("user")
                .addColumn(SqlColumnMeta.builder().name("id").typeName("INT").primaryKey(true).build())
                .build();

        boolean embedded = new DefaultDetectionStrategy().isEmbedded(table);
        assertThat(embedded).isFalse();
    }

    @Test
    public void testH2UserTableTranslation() throws Exception {
        BaseSchema schema = translated(new SqlSchemaTranslator(discovered()));

        assertThat(schema.connIdSchema().getObjectClassInfo()).isNotEmpty();
    }
}
