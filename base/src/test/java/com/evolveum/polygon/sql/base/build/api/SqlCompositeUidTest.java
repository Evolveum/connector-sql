/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.ValueTypeOverrideMapping;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for composite primary key UID mapping support — specifically
 * {@link SqlAttributeMapping.MultiColumn} created through factory and builder.
 */
@Test(singleThreaded = true)
public class SqlCompositeUidTest {

    // ── Factory creation tests ──

    private SqlAttributeMapping.SingleColumn col(String name, SqlSchemaValueMapping mapping) {
        return SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom(name), mapping, mapping);
    }

    @Test
    public void testCreateMultiColumnTwoColumns() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                List.of(col("dept_id", SqlSchemaValueMapping.INTEGER)), ".");
        assertThat(composite.additionalColumns()).hasSize(1);
        assertThat(composite.delimiter()).isEqualTo(".");
    }

    @Test
    public void testCreateMultiColumnThreeColumns() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                List.of(col("first", SqlSchemaValueMapping.INTEGER), col("second", SqlSchemaValueMapping.INTEGER)), ".");
        assertThat(composite.additionalColumns()).hasSize(2);
    }

    @Test
    public void testCreateMultiColumnDefaultDelimiter() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                List.of(col("v", SqlSchemaValueMapping.INTEGER)));
        assertThat(composite.delimiter()).isEqualTo(".");
    }

    @Test
    public void testCreateMultiColumnNoAdditionalColumns() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                Collections.emptyList(), ".");
        assertThat(composite.additionalColumns()).isEmpty();
    }

    // ── valuesFromAttribute (parameterized) ──

    @DataProvider
    public static Object[][] valuesFromAttributeProvider() {
        return new Object[][]{
                {"split-by-dot", 2, ".", "115.23", "115", "23"},
                {"split-by-colon", 2, ":", "10:alpha", "10", "alpha"},
                {"split-by-dash", 2, "-", "5-100", "5", "100"},
                {"split-three", 3, ".", "1.2.3", "1", "2", "3"},
                {"zero-values", 2, ".", "0.0", "0", "0"},
                {"large-values", 2, ".", "200.500", "200", "500"},
        };
    }

    @Test(dataProvider = "valuesFromAttributeProvider")
    public void testValuesFromAttribute(String name, int expected, String delimiter, String input, String... parts) {
        var cols = new ArrayList<SqlAttributeMapping.SingleColumn>();
        for (int i = 0; i < parts.length; i++) {
            cols.add(col("col" + i, SqlSchemaValueMapping.INTEGER));
        }
        var composite = SqlAttributeMapping.multiColumn(cols.getFirst(), cols.subList(1, cols.size()), delimiter);
        var result = composite.valuesFromAttribute(input);
        assertThat(result).hasSize(parts.length);
        for (int i = 0; i < parts.length; i++) {
            assertThat(result.get(i)).isEqualTo(parts[i]);
        }
    }

    @Test
    public void testValuesFromAttributeWithNull() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER), Collections.emptyList(), ".");
        assertThat(composite.valuesFromAttribute(null)).isEmpty();
    }

    @Test
    public void testValuesFromAttributeWithNonString() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER), Collections.emptyList(), ".");
        var result = composite.valuesFromAttribute(12345);
        assertThat(result).hasSize(1).containsExactly(12345);
    }

    @Test
    public void testValuesFromAttributePreservesCollection() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER), Collections.emptyList(), ".");
        var list = List.of("a", "b");
        assertThat(composite.valuesFromAttribute(list)).containsExactly("a", "b");
    }

    // ── column() returns main column ──

    @Test
    public void testColumnAlwaysMainColumn() {
        var composite = SqlAttributeMapping.multiColumn(
                col("pk1", SqlSchemaValueMapping.INTEGER),
                List.of(col("pk2", SqlSchemaValueMapping.INTEGER), col("pk3", SqlSchemaValueMapping.INTEGER)), ".");
        assertThat(composite.column().value()).isEqualTo("pk1");
    }

    // ── selectPaths returns all column paths ──

    @DataProvider
    public static Object[][] selectPathsProvider() {
        return new Object[][]{
                {"one-column", 0},
                {"two-columns", 1},
                {"three-columns", 2},
        };
    }

    @Test(dataProvider = "selectPathsProvider")
    public void testSelectPaths(String name, int additionalCount) {
        var cols = new ArrayList<SqlAttributeMapping.SingleColumn>();
        cols.add(col("id", SqlSchemaValueMapping.INTEGER));
        for (int i = 0; i < additionalCount; i++) {
            cols.add(col("col" + i, SqlSchemaValueMapping.INTEGER));
        }
        var composite = SqlAttributeMapping.multiColumn(cols.getFirst(), cols.subList(1, cols.size()), ".");
        PathBuilder<Object> tablePath = new PathBuilder<>(Object.class, "test_table");
        Collection<? extends Path<?>> paths = composite.selectPaths(tablePath);
        assertThat(paths).hasSize(cols.size());
    }

    // ── FilterSupport ──

    @Test
    public void testSqlFilterReturnsFilterSupport() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                List.of(col("dept_id", SqlSchemaValueMapping.INTEGER)), ".");
        assertThat(composite.sqlFilter())
                .isNotNull()
                .isInstanceOf(SqlAttributeMapping.FilterSupport.class);
    }

    // ── ConnId type, singleValue ──

    @Test
    public void testConnIdTypeIsString() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                List.of(col("dept_id", SqlSchemaValueMapping.INTEGER)), ".");
        assertThat(composite.connIdType()).isEqualTo(String.class);
    }

    @Test
    public void testSingleValueAndNull() {
        var composite = SqlAttributeMapping.multiColumn(
                col("id", SqlSchemaValueMapping.INTEGER),
                List.of(col("dept_id", SqlSchemaValueMapping.INTEGER)), ".");
        assertThat(composite.singleValueFromAttribute("10.20")).isEqualTo("10.20");
        assertThat(composite.singleValueFromAttribute(null)).isNull();
    }

    @Test
    public void testDefaultDelimiterConstant() {
        assertThat(SqlAttributeMapping.DEFAULT_DELIMITER).isEqualTo(".");
    }

    @Test
    public void testDefaultDelimiterInMultiColumn() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        assertThat(composite.delimiter()).isEqualTo(".");
        var result = composite.valuesFromAttribute("5.100");
        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("5");
        assertThat(result.get(1)).isEqualTo("100");
    }

    @Test
    public void testCustomDelimiterInMultiColumn() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("version"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), "-");
        assertThat(composite.delimiter()).isEqualTo("-");
        var result = composite.valuesFromAttribute("5-100");
        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("5");
        assertThat(result.get(1)).isEqualTo("100");
    }

    @Test
    public void testSingleColumnNullBypassesTypeOverrideConversion() {
        var stringToInteger = ValueTypeOverrideMapping.of(
                String.class, SqlSchemaValueMapping.INTEGER);
        var column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("numeric_value"),
                SqlSchemaValueMapping.INTEGER, stringToInteger);
        var table = new PathBuilder<>(Object.class, "test_table");

        assertThat(column.columnValues(table, "42").getFirst().value())
                .isEqualTo(42);
        assertThat(column.columnValues(table, null).getFirst().value())
                .isNull();
    }

    @Test
    public void testCompositeColumnValuesSplitAndConvertEveryPart() {
        var main = stringToIntegerColumn("id");
        var extra = stringToIntegerColumn("dept_id");
        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        var table = new PathBuilder<>(Object.class, "test_table");

        assertThat(composite.columnValues(table, "10.20"))
                .extracting(SqlAttributeMapping.ColumnValue::value)
                .containsExactly(10, 20);
    }

    @Test
    public void testCompositeColumnValuesAssignNullToEveryPart() {
        var main = stringToIntegerColumn("id");
        var extra = stringToIntegerColumn("dept_id");
        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        var table = new PathBuilder<>(Object.class, "test_table");

        assertThat(composite.columnValues(table, null))
                .extracting(SqlAttributeMapping.ColumnValue::value)
                .containsExactly(null, null);
    }

    @Test
    public void testCompositeColumnValuesRejectWrongPartCounts() {
        var main = stringToIntegerColumn("id");
        var extra = stringToIntegerColumn("dept_id");
        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        var table = new PathBuilder<>(Object.class, "test_table");

        assertThatThrownBy(() -> composite.columnValues(table, "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 2, got 1");
        assertThatThrownBy(() -> composite.columnValues(table, "10.20.30"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 2, got 3");
    }

    private SqlAttributeMapping.SingleColumn stringToIntegerColumn(String name) {
        return SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom(name),
                SqlSchemaValueMapping.INTEGER,
                ValueTypeOverrideMapping.of(String.class, SqlSchemaValueMapping.INTEGER));
    }

    // ── 7. Builder pattern: MultiColumn via SqlAttributeBuilderImpl ───────

    @Test
    public void testBuilderCreatesMultiColumn() {
        var builder = new SqlAttributeBuilderImpl(null, DefinitionValue.defaultFrom("id"));
        var mBuilder = builder.sql();
        mBuilder.valueMapping(DefinitionValue.detected(SqlSchemaValueMapping.INTEGER));
        mBuilder.additionalColumns().column("version", SqlSchemaValueMapping.INTEGER);
        var result = (SqlAttributeMapping.MultiColumn) mBuilder.build();
        assertThat(result.additionalColumns()).hasSize(1);
        assertThat(result.column().value()).isEqualTo("id");
        assertThat(result.delimiter()).isEqualTo(".");
    }

    @Test
    public void testBuilderMultipleExtraColumns() {
        var builder = new SqlAttributeBuilderImpl(null, DefinitionValue.defaultFrom("id"));
        var mBuilder = builder.sql();
        mBuilder.valueMapping(DefinitionValue.detected(SqlSchemaValueMapping.INTEGER));
        var uidCols = mBuilder.additionalColumns();
        uidCols.column("first", SqlSchemaValueMapping.INTEGER);
        uidCols.column("second", SqlSchemaValueMapping.INTEGER);
        var result = (SqlAttributeMapping.MultiColumn) mBuilder.build();
        assertThat(result.additionalColumns()).hasSize(2);
    }
<<<<<<< HEAD

    @Test
    public void testBuilderDefaultDelimiter() {
        var builder = new SqlAttributeBuilderImpl(null, DefinitionValue.defaultFrom("id"));
        var mBuilder = builder.sql();
        mBuilder.valueMapping(DefinitionValue.detected(SqlSchemaValueMapping.INTEGER));
        mBuilder.additionalColumns().column("v", SqlSchemaValueMapping.INTEGER);

        var result = mBuilder.build();
        assertThat(result).isInstanceOf(SqlAttributeMapping.MultiColumn.class);
        var composite = (SqlAttributeMapping.MultiColumn) result;
        assertThat(composite.delimiter()).isEqualTo(".");
    }
}
