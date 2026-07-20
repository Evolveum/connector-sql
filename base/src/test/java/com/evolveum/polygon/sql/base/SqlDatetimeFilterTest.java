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
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.testng.annotations.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;

import static org.identityconnectors.framework.common.objects.AttributeBuilder.build;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for datetime filter support in {@link SqlAttributeMapping} and {@link SqlSchemaValueMapping}.
 * Tests value conversion, filter predicate generation, and composite UID conversion.
 */
@Test(singleThreaded = true)
public class SqlDatetimeFilterTest {

    // ─── toWireValue conversions (SqlSchemaValueMapping) ─────────────────

    @Test
    public void dateToWireValueWithStringYyyyMmDd() {
        var result = SqlSchemaValueMapping.DATE.toWireValue("2024-06-15");
        assertThat(result).isInstanceOf(Date.class);
        assertThat(result).isEqualTo(Date.valueOf("2024-06-15"));
    }

    @Test
    public void dateToWireValueWithLocalDate() {
        var result = SqlSchemaValueMapping.DATE.toWireValue(LocalDate.of(2024, 3, 20));
        assertThat(result).isInstanceOf(Date.class);
        assertThat((Date) result).isEqualTo(Date.valueOf("2024-03-20"));
    }

    @Test
    public void dateToWireValueWithPassthrough() {
        var input = Date.valueOf("2024-12-25");
        var result = SqlSchemaValueMapping.DATE.toWireValue(input);
        assertThat(result).isSameAs(input);
    }

    @Test
    public void dateToWireValueWithTimestampString() {
        // String like "2024-06-15 10:00:00" should be parsed by taking first 10 chars
        var result = SqlSchemaValueMapping.DATE.toWireValue("2024-06-15 10:00:00");
        assertThat(result).isInstanceOf(Date.class);
        assertThat(result).isEqualTo(Date.valueOf("2024-06-15"));
    }

    @Test
    public void dateToWireValueWithNull() {
        var result = SqlSchemaValueMapping.DATE.toWireValue(null);
        assertThat(result).isNull();
    }

    @Test
    public void dateToWireValueWithUnparseableString() {
        assertThatThrownBy(() -> SqlSchemaValueMapping.DATE.toWireValue("not-a-date"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot parse string");
    }

    @Test
    public void timeToWireValueWithStringFull() {
        var result = SqlSchemaValueMapping.TIME.toWireValue("14:30:45");
        assertThat(result).isInstanceOf(Time.class);
        assertThat(result).isEqualTo(Time.valueOf("14:30:45"));
    }

    @Test
    public void timeToWireValueWithLocalTime() {
        var result = SqlSchemaValueMapping.TIME.toWireValue(LocalTime.of(9, 15, 30));
        assertThat(result).isInstanceOf(Time.class);
        assertThat(result).isEqualTo(Time.valueOf("09:15:30"));
    }

    @Test
    public void timeToWireValueWithPassthrough() {
        var input = Time.valueOf("20:00:00");
        var result = SqlSchemaValueMapping.TIME.toWireValue(input);
        assertThat(result).isSameAs(input);
    }

    @Test
    public void timeToWireValueWithTimestampString() {
        // String like "2024-06-15 14:30:45" should extract time part
        var result = SqlSchemaValueMapping.TIME.toWireValue("2024-06-15 14:30:45");
        assertThat(result).isInstanceOf(Time.class);
        assertThat(result).isEqualTo(Time.valueOf("14:30:45"));
    }

    @Test
    public void timeToWireValueWithNull() {
        var result = SqlSchemaValueMapping.TIME.toWireValue(null);
        assertThat(result).isNull();
    }

    @Test
    public void timestampToWireValueWithString() {
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue("2024-06-15 10:30:00");
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(result).isEqualTo(Timestamp.valueOf("2024-06-15 10:30:00"));
    }

    @Test
    public void timestampToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime()).isEqualTo(zdt.toLocalDateTime());
    }

    @Test
    public void timestampToWireValueWithLocalDate() {
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(LocalDate.of(2024, 6, 15));
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 6, 15, 0, 0, 0));
    }

    @Test
    public void timestampToWireValueWithISO8601String() {
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue("2024-06-15T10:30:00");
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime()).isEqualTo(LocalDateTime.of(2024, 6, 15, 10, 30, 0));
    }

    @Test
    public void timestampToWireValueWithZonedDateTimeAsFilterValue() {
        var zdt = ZonedDateTime.parse("2024-06-15T10:30:00+02:00");
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(Instant.ofEpochMilli(((Timestamp) result).getTime())).isEqualTo(zdt.toInstant());
    }

    @Test
    public void timestampWithTimezoneToWireValueWithString() {
        var result = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue("2024-06-15 10:30:00");
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(result).isEqualTo(Timestamp.valueOf("2024-06-15 10:30:00"));
    }

    @Test
    public void timestampWithTimezoneToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        var result = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
    }

    @Test
    public void timestampWithTimezoneToWireValueWithNull() {
        var result = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue(null);
        assertThat(result).isNull();
    }

    @Test
    public void varcharToWireValueWithString() {
        var result = SqlSchemaValueMapping.VARCHAR.toWireValue("hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    public void varcharToWireValueWithNull() {
        var result = SqlSchemaValueMapping.VARCHAR.toWireValue(null);
        assertThat(result).isNull();
    }

    // ─── Filter predicate generation (SqlAttributeMapping.SingleColumn) ───

    private static RelationalPathBase<?> createTablePath(String name) {
        return new TestTablePath(Object.class, name, null, null);
    }

    private static class TestTablePath extends RelationalPathBase<Object> {
        public TestTablePath(Class<?> type, String metadata, String schema, String table) {
            super(type, metadata, schema, table);
        }
    }

    @Test
    public void dateEqualsFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.equalTo(build("date_col", "2024-06-15"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void dateGreaterThanFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.greaterThan(build("date_col", "2024-06-01"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void dateLessThanFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.lessThan(build("date_col", "2024-06-01"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void dateGreaterThanOrEqualFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.greaterThanOrEqualTo(build("date_col", "2024-06-01"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void dateLessThanOrEqualFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.lessThanOrEqualTo(build("date_col", "2024-06-01"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void dateEqualsFilterWithZonedDateTimeValue() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 0, 0, 0, ZoneId.systemDefault());
        var filter = FilterBuilder.equalTo(build("date_col", zdt));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void timestampEqualsFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var filter = FilterBuilder.equalTo(build("ts_col", "2024-06-15 10:30:00"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void timestampGreaterThanFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var filter = FilterBuilder.greaterThan(build("ts_col", "2024-06-01 00:00:00"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void timestampLessThanFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var filter = FilterBuilder.lessThan(build("ts_col", "2024-06-01 00:00:00"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void timestampGreaterThanOrEqualFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var filter = FilterBuilder.greaterThanOrEqualTo(build("ts_col", "2024-06-01 00:00:00"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void timestampLessThanOrEqualFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var filter = FilterBuilder.lessThanOrEqualTo(build("ts_col", "2024-06-01 00:00:00"));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

@Test
    public void timestampEqualsFilterWithInstantValue() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var instantStr = Instant.parse("2024-06-15T10:30:00Z");
        var zdt = instantStr.atZone(ZoneId.systemDefault());
        var filter = FilterBuilder.equalTo(build("ts_col", zdt));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    @Test
    public void timestampEqualsFilterWithZonedDateTimeValue() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("ts_col"),
                SqlSchemaValueMapping.TIMESTAMP,
                SqlSchemaValueMapping.TIMESTAMP
        );
        var zdt = ZonedDateTime.parse("2024-06-15T10:30:00+02:00");
        var filter = FilterBuilder.equalTo(build("ts_col", zdt));
        var pred = column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter);
        assertThat(pred).isNotNull();
    }

    // ─── Composite UID datetime conversion ──────────────────────────────

    @Test
    public void compositeUidMultipleParts() {
        SqlAttributeMapping.SingleColumn main = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk1"),
                SqlSchemaValueMapping.INTEGER,
                SqlSchemaValueMapping.INTEGER
        );
        var extra = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("pk2"),
                SqlSchemaValueMapping.INTEGER,
                SqlSchemaValueMapping.INTEGER
        );
        var composite = SqlAttributeMapping.multiColumn(main, List.of(extra), ".");

var result = composite.valuesFromAttribute("5.100");
        assertThat(result).hasSize(2);
        assertThat(result.getFirst()).isEqualTo("5");
        assertThat(result.get(1)).isEqualTo("100");
    }
}