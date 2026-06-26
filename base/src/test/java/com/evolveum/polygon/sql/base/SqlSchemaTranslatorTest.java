package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlSchema;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.evolveum.polygon.sql.base.schema.SqlSchemaTranslator;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import com.evolveum.polygon.sql.base.schema.strategy.DefaultDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.NamedColumnUidStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.NonPkColumnsOnlyStrategy;
import com.evolveum.polygon.sql.base.schema.strategy.PrefixEmbeddedDetectionStrategy;
import com.evolveum.polygon.sql.base.test.H2DatabaseInitializer;
import org.identityconnectors.framework.common.objects.Schema;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Arrays;
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

    @Test
    public void testDefaultStrategySinglePkUid() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context); detector.discover(); SqlSchemaTranslator translator = new SqlSchemaTranslator(context.schema())
                .addStrategy(new DefaultDetectionStrategy());

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testNamedColumnUidStrategy() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context); detector.discover(); SqlSchemaTranslator translator = new SqlSchemaTranslator(context.schema())
                .addStrategy(new NamedColumnUidStrategy("uid"));

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testNonPkColumnsOnlyStrategy() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context); detector.discover(); SqlSchemaTranslator translator = new SqlSchemaTranslator(context.schema())
                .addStrategy(new NonPkColumnsOnlyStrategy());

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testPrefixEmbeddedDetectionStrategy() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context); detector.discover(); SqlSchemaTranslator translator = new SqlSchemaTranslator(context.schema())
                .addStrategy(new PrefixEmbeddedDetectionStrategy("user"));

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testStrategyComposition() throws Exception {
        SqlSchemaDetector detector = new SqlSchemaDetector(context); detector.discover(); SqlSchemaTranslator translator = new SqlSchemaTranslator(context.schema())
                .addStrategy(new NamedColumnUidStrategy("id"))
                .addStrategy(new NonPkColumnsOnlyStrategy())
                .addStrategy(new PrefixEmbeddedDetectionStrategy("user"));

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).hasSize(6);
    }

    @Test
    public void testEmptySchema() throws Exception {
        SqlSchema emptySchema = new SqlSchema(Arrays.asList());

        SqlSchemaTranslator translator = new SqlSchemaTranslator(emptySchema);

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).isEmpty();
    }

    @Test
    public void testNullSchema() throws Exception {
        SqlSchemaTranslator translator = new SqlSchemaTranslator(null);

        translator.translate();

        assertThat(translator.toConnIdSchema().getObjectClassInfo()).isEmpty();
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
        SqlSchemaDetector detector = new SqlSchemaDetector(context);
        detector.discover();

        SqlSchemaTranslator translator = new SqlSchemaTranslator(context.schema());
        translator.translate();

        Schema connIdSchema = translator.toConnIdSchema();

        assertThat(connIdSchema.getObjectClassInfo()).isNotEmpty();
    }
}