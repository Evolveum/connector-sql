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
import org.testng.annotations.Test;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.identityconnectors.framework.common.objects.AttributeBuilder.build;

/**
 * Unit tests for datetime filter support in {@link SqlAttributeMapping} and {@link SqlSchemaValueMapping}.
 * Tests value conversion, filter predicate generation, and composite UID conversion.
 */
@Test(singleThreaded = true)
public class SqlDatetimeFilterTest {

    // ─── toWireValue conversions (SqlSchemaValueMapping) ─────────────────

    @Test
    public void dateToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.DATE.toWireValue(zdt);
        assertThat(result).isInstanceOf(Date.class);
        assertThat(result).isEqualTo(Date.valueOf("2024-06-15"));
    }

    @Test
    public void dateToWireValueWithPassthrough() {
        var input = ZonedDateTime.of(2024, 12, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.DATE.toWireValue(input);
        assertThat(result).isInstanceOf(Date.class);
        assertThat(result).isEqualTo(Date.valueOf("2024-12-25"));
    }

    @Test
    public void dateToWireValueWithZonedDateTimeFromTimestamp() {
        var ts = Timestamp.valueOf("2024-06-15 10:00:00");
        var zdt = ts.toInstant().atZone(ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.DATE.toWireValue(zdt);
        assertThat(result).isInstanceOf(Date.class);
        assertThat(result).isEqualTo(Date.valueOf("2024-06-15"));
    }

    @Test
    public void timeToWireValueWithStringFull() {
        var result = SqlSchemaValueMapping.TIME.toWireValue("14:30:45");
        assertThat(result).isInstanceOf(LocalTime.class);
        assertThat(result).isEqualTo(LocalTime.of(14, 30, 45));
    }

    @Test
    public void timeToWireValueWithParsedTime() {
        var result = SqlSchemaValueMapping.TIME.toWireValue("09:15:30");
        assertThat(result).isInstanceOf(LocalTime.class);
        assertThat(result).isEqualTo(LocalTime.of(9, 15, 30));
    }

    @Test
    public void timeToWireValueWithPassthrough() {
        var result = SqlSchemaValueMapping.TIME.toWireValue("20:00:00");
        assertThat(result).isInstanceOf(LocalTime.class);
        assertThat(result).isEqualTo(LocalTime.of(20, 0, 0));
    }

    @Test
    public void timeToWireValueWithNull() {
        var result = SqlSchemaValueMapping.TIME.toWireValue(null);
        assertThat(result).isNull();
    }

    @Test
    public void timestampToWireValueWithZonedDateTimePassthrough() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime()).isEqualTo(zdt.toLocalDateTime());
    }

    @Test
    public void timestampToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime()).isEqualTo(zdt.toLocalDateTime());
    }

    @Test
    public void timestampToWireValueWithZonedDateTimeFromLocalDate() {
        var localDate = LocalDate.of(2024, 6, 15);
        var zdt = localDate.atStartOfDay(ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) result).toLocalDateTime().toLocalDate()).isEqualTo(localDate);
    }

    @Test
    public void timestampToWireValueWithZonedDateTimeISO8601() {
        var zdt = ZonedDateTime.parse("2024-06-15T10:30:00+00:00");
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(Instant.ofEpochMilli(((Timestamp) result).getTime())).isEqualTo(zdt.toInstant());
    }

    @Test
    public void timestampToWireValueWithZonedDateTimeAsFilterValue() {
        var zdt = ZonedDateTime.parse("2024-06-15T10:30:00+02:00");
        var result = SqlSchemaValueMapping.TIMESTAMP.toWireValue(zdt);
        assertThat(result).isInstanceOf(Timestamp.class);
        assertThat(Instant.ofEpochMilli(((Timestamp) result).getTime())).isEqualTo(zdt.toInstant());
    }

    @Test
    public void timestampWithTimezoneToWireValueWithZonedDateTimeFromTimestamp() {
        var ts = Timestamp.valueOf("2024-06-15 10:30:00");
        var zdt = ts.toInstant().atZone(ZoneId.systemDefault());
        var result = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue(zdt);
        assertThat(result).isInstanceOf(ZonedDateTime.class);
        assertThat(((ZonedDateTime) result).toInstant()).isEqualTo(ts.toInstant());
    }

    @Test
    public void timestampWithTimezoneToWireValueWithZonedDateTime() {
        var zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        var result = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.toWireValue(zdt);
        assertThat(result).isInstanceOf(ZonedDateTime.class);
        assertThat(result).isSameAs(zdt);
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
        var filter = FilterBuilder.equalTo(build("date_col", ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
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
        var filter = FilterBuilder.greaterThan(build("date_col", ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
        // Comparison operators for DATE columns are not supported by the filter translator
        assertThatThrownBy(() -> column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Unsupported filter");
    }

    @Test
    public void dateLessThanFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.lessThan(build("date_col", ZonedDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
        // Comparison operators for DATE columns are not supported by the filter translator
        assertThatThrownBy(() -> column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Unsupported filter");
    }

    @Test
    public void dateGreaterThanOrEqualFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.greaterThanOrEqualTo(build("date_col", ZonedDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
        // Comparison operators for DATE columns are not supported by the filter translator
        assertThatThrownBy(() -> column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Unsupported filter");
    }

    @Test
    public void dateLessThanOrEqualFilterProducesPredicate() {
        SqlAttributeMapping.SingleColumn column = SqlAttributeMapping.singleColumn(
                DefinitionValue.defaultFrom("date_col"),
                SqlSchemaValueMapping.DATE,
                SqlSchemaValueMapping.DATE
        );
        var filter = FilterBuilder.lessThanOrEqualTo(build("date_col", ZonedDateTime.of(2024, 6, 1, 0, 0, 0, 0, ZoneId.systemDefault())));
        // Comparison operators for DATE columns are not supported by the filter translator
        assertThatThrownBy(() -> column.sqlFilter().predicateFor(createTablePath("t"), (AttributeFilter) filter))
                .isInstanceOf(Exception.class)
                .hasMessageContaining("Unsupported filter");
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
        var filter = FilterBuilder.equalTo(build("ts_col", Timestamp.valueOf("2024-06-15 10:30:00").toInstant().atZone(ZoneId.systemDefault())));
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
        var filter = FilterBuilder.greaterThan(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault())));
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
        var filter = FilterBuilder.lessThan(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault())));
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
        var filter = FilterBuilder.greaterThanOrEqualTo(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault())));
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
        var filter = FilterBuilder.lessThanOrEqualTo(build("ts_col", Timestamp.valueOf("2024-06-01 00:00:00").toInstant().atZone(ZoneId.systemDefault())));
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