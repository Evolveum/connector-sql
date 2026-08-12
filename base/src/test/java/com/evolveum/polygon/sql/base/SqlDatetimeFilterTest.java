/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.identityconnectors.framework.common.objects.AttributeBuilder.build;

/**
 * Unit tests for datetime filter support in {@link SqlAttributeMapping}.
 * <p>
 * Tests filter predicate generation and composite UID conversion.
 * The {@code toWireValue} conversions for DATE/TIME/TIMESTAMP are covered by
 * {@link SqlSchemaValueMappingTest} which tests the value mapping layer independently.
 * </p>
 */
@Test(singleThreaded = true)
public class SqlDatetimeFilterTest {

    // ─── Helper ───

    private static RelationalPathBase<?> createTablePath(String name) {
        return new TestTablePath(Object.class, name, null, null);
    }

    private static class TestTablePath extends RelationalPathBase<Object> {
        public TestTablePath(Class<?> type, String metadata, String schema, String table) {
            super(type, metadata, schema, table);
        }
    }

    // ─── Filter predicate generation ───

    // DATE filter: equals works; comparison operators unsupported

    @Test
    public void dateEqualsFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE, SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.equalTo(build("date_col", ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
        assertThat(column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter)).isNotNull();
    }

    @Test
    public void dateEqualsFilterWithZonedDateTimeValue() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE, SqlSchemaValueMapping.DATE
        );
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.systemDefault());
        var filter = FilterBuilder.equalTo(build("date_col", zdt));
        assertThat(column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter)).isNotNull();
    }

    // DATE comparison operators are unsupported

    @DataProvider
    public static Object[][] dateComparisonFilters() {
        return new Object[][]{
                {"greaterThan", FilterBuilder.greaterThan(build("date_col", ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())))},
                {"lessThan", FilterBuilder.lessThan(build("date_col", ZonedDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneId.systemDefault())))},
                {"greaterThanOrEqualTo", FilterBuilder.greaterThanOrEqualTo(build("date_col", ZonedDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneId.systemDefault())))},
                {"lessThanOrEqualTo", FilterBuilder.lessThanOrEqualTo(build("date_col", ZonedDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneId.systemDefault())))},
        };
    }

    @Test(dataProvider = "dateComparisonFilters")
    public void dateComparisonFiltersUnsupported(String filterType, Object filter) {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE, SqlSchemaValueMapping.DATE
        );
        assertThatThrownBy(() -> column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Unsupported filter");
    }

    // TIMESTAMP filters: comparison operators work

    @DataProvider
    public static Object[][] timestampFilters() {
        var ts = Timestamp.valueOf("2024-06-15 10:30:00");
        var zdt = ts.toInstant().atZone(ZoneId.systemDefault());
        return new Object[][]{
                {"timestamp-equals",
                        FilterBuilder.equalTo(build("ts_col", zdt)), true},
                {"timestamp-greaterThan",
                        FilterBuilder.greaterThan(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault()))), true},
                {"timestamp-lessThan",
                        FilterBuilder.lessThan(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault()))), true},
                {"timestamp-lessThanOrEqual",
                        FilterBuilder.lessThanOrEqualTo(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault()))), true},
                {"timestamp-greaterThanOrEqual",
                        FilterBuilder.greaterThanOrEqualTo(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault()))), true},
                {"timestamp-equals-instant",
                        FilterBuilder.equalTo(build("ts_col", Instant.parse("2024-06-15T10:30:00Z").atZone(ZoneId.systemDefault()))), true},
                {"timestamp-equals-parsed",
                        FilterBuilder.equalTo(build("ts_col", ZonedDateTime.parse("2024-06-15T10:30:00+02:00"))), true},
        };
    }

    @Test(dataProvider = "timestampFilters")
    public void timestampFilterPredicates(String name, Object filter, boolean shouldProduce) {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP, SqlSchemaValueMapping.TIMESTAMP
        );
        if (shouldProduce) {
            assertThat(column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter)).isNotNull();
        } else {
            assertThatThrownBy(() -> column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter))
                    .isInstanceOf(Exception.class);
        }
    }

    // ─── Wire value conversions (integration with filter predicates) ───
    // These verify the full pipeline: ConnId value → wire value → SQL query parameter
    // Unit-level toWireValue tests are in SqlSchemaValueMappingTest

    @Test
    public void dateToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.DATE.toWireValue(zdt);
        assertThat(result).isInstanceOf(Date.class).isEqualTo(Date.valueOf("2024-06-15"));
    }

    @Test
    public void timeToWireValueWithString() {
        var result = SqlSchemaValueMapping.TIME.toWireValue("14:30:45");
        assertThat(result).isInstanceOf(LocalTime.class).isEqualTo(LocalTime.of(14, 30, 45));
    }

    @Test
    public void timestampToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime()).isEqualTo(zdt.toLocalDateTime());
    }

    @Test
    public void timestampToWireValueWithTimezone() {
        var zdt = ZonedDateTime.parse("2024-06-15T10:30:00+00:00");
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(Instant.ofEpochMilli(((Timestamp) result).getTime())).isEqualTo(zdt.toInstant());
    }

    @Test
    public void timestampWithTimezoneToWireValuePassthrough() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        assertThat(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue(zdt)).isSameAs(zdt);
    }

    @Test
    public void timestampWithTimezoneToWireValueNull() {
        assertThat(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue(null)).isNull();
    }

    @Test
    public void varcharToWireValue() {
        assertThat(SqlSchemaValueMapping.VARCHAR.toWireValue("hello")).isEqualTo("hello");
        assertThat(SqlSchemaValueMapping.VARCHAR.toWireValue(null)).isNull();
    }

    // ─── Composite UID datetime conversion ───

    @Test
    public void compositeUidMultipleParts() {
        SqlAttributeMapping.SingleColumn main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk1"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER
        );
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk2"),
                SqlSchemaValueMapping.INTEGER, SqlSchemaValueMapping.INTEGER
        );
        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");
        var result = composite.valuesFromAttribute("5.100");
        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("5");
        assertThat(result.get(1)).isEqualTo("100");
    }
}
