/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.PathBuilder;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for composite primary key UID mapping support — specifically
 * {@link SqlAttributeMapping.MultiColumn} created through the
 * SqlAttributeMapping factory and the SqlAttributeBuilderImpl builder.
 */
@Test(singleThreaded = true)
public class SqlCompositeUidTest {

    // ── 1. Creating a MultiColumn mapping ──────────────────────────────────

    @Test
    public void testCreateMultiColumnTwoColumns() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");

        assertThat(composite).isNotNull();
        assertThat(composite.mainColumn()).isSameAs(main);
        assertThat(composite.additionalColumns()).containsExactly(extra);
        assertThat(composite.delimiter()).isEqualTo(".");
    }

    @Test
    public void testCreateMultiColumnThreeColumns() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e1 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("first"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e2 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("second"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(e1, e2), ".");

        assertThat(composite.mainColumn()).isSameAs(main);
        assertThat(composite.additionalColumns()).hasSize(2);
        assertThat(composite.additionalColumns().getFirst()).isSameAs(e1);
        assertThat(composite.additionalColumns().get(1)).isSameAs(e2);
    }

    @Test
    public void testCreateMultiColumnNoAdditionalColumns() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, Collections.emptyList(), ".");

        assertThat(composite).isNotNull();
        assertThat(composite.mainColumn()).isSameAs(main);
        assertThat(composite.additionalColumns()).isEmpty();
    }

    @Test
    public void testCreateMultiColumnDefaultDelimiter() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("v"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra));

        assertThat(composite.delimiter()).isEqualTo(".");
    }

    // ── 2. valuesFromAttribute splits by delimiter ────────────────────────

    @Test
    public void testValuesFromAttributeSplitsByDot() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        var result = composite.valuesFromAttribute("115.23");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("115");
        assertThat(result.get(1)).isEqualTo("23");
    }

    @Test
    public void testValuesFromAttributeSplitsByCustomDelimiter() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("code"),
                SqlSchemaValueMapping.VARCHAR, SqlSchemaValueMapping.VARCHAR);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ":");
        var result = composite.valuesFromAttribute("10:alpha");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("10");
        assertThat(result.get(1)).isEqualTo("alpha");
    }

    @Test
    public void testValuesFromAttributeSplitsThreeParts() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e1 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("first"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e2 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("second"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(e1, e2), ".");
        var result = composite.valuesFromAttribute("1.2.3");

        assertThat(result).hasSize(3);
        assertThat(result.getFirst()).isEqualTo("1");
        assertThat(result.get(1)).isEqualTo("2");
        assertThat(result.get(2)).isEqualTo("3");
    }

    @Test
    public void testValuesFromAttributeWithNull() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var composite = SqlAttributeMapping.multiColumn(main, Collections.emptyList(), ".");

        assertThat(composite.valuesFromAttribute(null)).isEmpty();
    }

    @Test
    public void testValuesFromAttributeWithNonString() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var composite = SqlAttributeMapping.multiColumn(main, Collections.emptyList(), ".");

        var result = composite.valuesFromAttribute(12345);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(12345);
    }

    @Test
    public void testValuesFromAttributeWithZero() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        var result = composite.valuesFromAttribute("0.0");

        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("0");
        assertThat(result.get(1)).isEqualTo("0");
    }

    @Test
    public void testValuesFromAttributeWithNumericRange() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        for (String uid : List.of("10.1", "200.500", "1.0")) {
            var result = composite.valuesFromAttribute(uid);
            assertThat(result).hasSize(2);
        }
    }

    @Test
    public void testValuesFromAttributePreservesCollection() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var composite = SqlAttributeMapping.multiColumn(main, Collections.emptyList(), ".");

        var list = new ArrayList<String>();
        list.add("a");
        list.add("b");
        var result = composite.valuesFromAttribute((Object) list);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly("a", "b");
    }

    // ── 3. column() returns main column ───────────────────────────────────

    @Test
    public void testColumnReturnsMainColumn() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("order_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("version"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        assertThat(composite.column().value()).isEqualTo("order_id");
    }

    @Test
    public void testColumnAlwaysMainColumnWithExtras() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk1"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e1 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk2"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e2 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk3"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(e1, e2), ".");
        assertThat(composite.column().value()).isEqualTo("pk1");
    }

    // ── 4. selectPaths returns all column paths ───────────────────────────

    @Test
    public void testSelectPathsReturnsTwoColumns() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        PathBuilder<Object> tablePath = new PathBuilder<>(Object.class, "test_table");
        Collection<? extends Path<?>> paths = composite.selectPaths(tablePath);

        assertThat(paths).hasSize(2);
    }

    @Test
    public void testSelectPathsReturnsThreeColumns() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e1 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("first"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var e2 = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("second"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(e1, e2), ".");
        PathBuilder<Object> tablePath = new PathBuilder<>(Object.class, "test_table");
        Collection<? extends Path<?>> paths = composite.selectPaths(tablePath);

        assertThat(paths).hasSize(3);
    }

    @Test
    public void testSelectPathsNoAdditional() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, Collections.emptyList(), ".");
        PathBuilder<Object> tablePath = new PathBuilder<>(Object.class, "test_table");
        Collection<? extends Path<?>> paths = composite.selectPaths(tablePath);

        assertThat(paths).hasSize(1);
    }

    // ── 5. sqlFilter returns non-null FilterSupport ───────────────────────

    @Test
    public void testSqlFilterReturnsFilterSupport() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        assertThat(composite.sqlFilter()).isNotNull();
    }

    @Test
    public void testSqlFilterFilterSupportIsCorrectType() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        assertThat(composite.sqlFilter()).isInstanceOf(SqlAttributeMapping.FilterSupport.class);
    }

    // ── 6. ConnId type, singleValue, delimiter ────────────────────────────

    @Test
    public void testConnIdTypeIsString() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        assertThat(composite.connIdType()).isEqualTo(String.class);
    }

    @Test
    public void testSingleValueFromAttributeReturnsSameValue() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        assertThat(composite.singleValueFromAttribute("10.20")).isEqualTo("10.20");
    }

    @Test
    public void testSingleValueFromAttributeNull() {
        var main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("dept_id"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER);

        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
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

    // ── 7. Builder pattern: MultiColumn via SqlAttributeBuilderImpl ───────

    @Test
    public void testBuilderCreatesMultiColumn() {
        var builder = new SqlAttributeBuilderImpl(null, DefinitionValue.defaultFrom("id"));
        var mBuilder = builder.sql();

        mBuilder.valueMapping(DefinitionValue.detected(SqlSchemaValueMapping.INTEGER));
        mBuilder.additionalColumns()
                .column("version", SqlSchemaValueMapping.INTEGER);

        var result = mBuilder.build();

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(SqlAttributeMapping.MultiColumn.class);

        var composite = (SqlAttributeMapping.MultiColumn) result;
        assertThat(composite.mainColumn()).isNotNull();
        assertThat(composite.additionalColumns()).hasSize(1);
        assertThat(composite.column().value()).isEqualTo("id");
    }

    @Test
    public void testBuilderTwoExtraColumns() {
        var builder = new SqlAttributeBuilderImpl(null, DefinitionValue.defaultFrom("id"));
        var mBuilder = builder.sql();

        mBuilder.valueMapping(DefinitionValue.detected(SqlSchemaValueMapping.INTEGER));
        var uidCols = mBuilder.additionalColumns();
        uidCols.column("first", SqlSchemaValueMapping.INTEGER);
        uidCols.column("second", SqlSchemaValueMapping.INTEGER);

        var result = mBuilder.build();

        assertThat(result).isInstanceOf(SqlAttributeMapping.MultiColumn.class);
        var composite = (SqlAttributeMapping.MultiColumn) result;
        assertThat(composite.additionalColumns()).hasSize(2);
    }

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