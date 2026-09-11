/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import org.assertj.core.api.Assertions;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.xml.datatype.DatatypeFactory;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueryDslTypeMapping} — QueryDSL Java type ↔ ConnId type conversions.
 */
@Test(singleThreaded = true)
public class QueryDslTypeMappingTest {


    // ── Conversion logic ──────────────────────────────────────────────

    @Test
    public void testSmallIntConversion() {
        var mapping = QueryDslTypeMapping.SMALL_INT;
        assertThat(mapping.toConnIdValue((short) 42)).isEqualTo(42);
        assertThat((Integer) mapping.toConnIdValue((short) 100)).isEqualTo(100);
        assertThat(mapping.toWireValue(42)).isEqualTo((short) 42);
    }

    @Test
    public void testFloatConversion() {
        var mapping = QueryDslTypeMapping.FLOAT_NUM;
        var connId = (Double) mapping.toConnIdValue(1.5f);
        assertThat(connId).isCloseTo(1.5, Assertions.within(0.001));
        var wire = (Float) mapping.toWireValue(1.5);
        assertThat((double) wire).isCloseTo(1.5, Assertions.within(0.001));
    }


    @Test
    public void testDateConversion() {
        var mapping = QueryDslTypeMapping.SQL_DATE;
        Date sqlDate = Date.valueOf("2024-06-15");
        ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault());
        var connId = (ZonedDateTime) mapping.toConnIdValue(sqlDate);
        assertThat(connId.toLocalDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        var back = (Date) mapping.toWireValue(zdt);
        assertThat(back).isEqualTo(sqlDate);
    }

    @Test
    public void testTimeConversion() {
        var mapping = QueryDslTypeMapping.SQL_TIME;
        Time sqlTime = Time.valueOf("14:30:00");
        var lt = sqlTime.toLocalTime();
        var connId = (String) mapping.toConnIdValue(lt);
        assertThat(connId).isEqualTo(lt.toString());
        var back = (LocalTime) mapping.toWireValue("14:30:00");
        assertThat(back).isEqualTo(lt);
    }

    @Test
    public void testTimestampConversion() {
        var mapping = QueryDslTypeMapping.SQL_TIMESTAMP;
        Timestamp ts = Timestamp.valueOf("2024-06-15 14:30:00");
        var connId = (ZonedDateTime) mapping.toConnIdValue(ts);
        assertThat(connId).isInstanceOf(ZonedDateTime.class);
        var back = (Timestamp) mapping.toWireValue(connId);
        assertThat(back.toInstant()).isEqualTo(ts.toInstant());
    }

    @DataProvider
    public static Object[][] timestampTimeZones() {
        return new Object[][]{{"UTC"}, {"Europe/Bratislava"}};
    }

    @Test(dataProvider = "timestampTimeZones")
    public void testTimestampCalendarBoundaries(String timeZone) throws Exception {
        var originalTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
            var xmlFactory = DatatypeFactory.newInstance();
            for (var value : new String[]{
                    "0001-01-01T00:00:00.123456789", "1500-01-01T00:00:00",
                    "1899-12-31T23:59:59", "1900-01-01T00:00:00",
                    "2001-06-15T14:30:00", "9999-12-31T00:00:00"}) {
                var fields = LocalDateTime.parse(value);
                var timestamp = Timestamp.valueOf(fields);
                var converted = (ZonedDateTime) QueryDslTypeMapping.SQL_TIMESTAMP.toConnIdValue(timestamp);
                var expected = fields.getYear() < 1900
                        ? fields.atZone(ZoneOffset.UTC)
                        : timestamp.toInstant().atZone(ZoneId.systemDefault());

                assertThat(converted).as("%s in %s", value, timeZone).isEqualTo(expected);
                assertThat(converted.getZone()).isEqualTo(expected.getZone());
                var xml = xmlFactory.newXMLGregorianCalendar(converted.toInstant().toString());
                assertThat(xml.isValid()).isTrue();
                assertThat(QueryDslTypeMapping.SQL_TIMESTAMP.toWireValue(converted)).isEqualTo(timestamp);

                if (fields.getYear() < 1900) {
                    assertThat(xml.getYear()).isEqualTo(fields.getYear());
                    // Historical writes preserve fields, deliberately ignoring the supplied offset.
                    assertThat(QueryDslTypeMapping.SQL_TIMESTAMP.toWireValue(
                            fields.atZone(ZoneOffset.ofHours(2)))).isEqualTo(timestamp);
                }
            }
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void testTimestampTZConversion() {
        var mapping = QueryDslTypeMapping.SQL_TIMESTAMP_TZ;
        ZonedDateTime zdt = ZonedDateTime.of(2024, 6, 15, 10, 30, 0, 0, ZoneId.of("UTC"));
        assertThat(mapping.toConnIdValue(zdt)).isSameAs(zdt);
        assertThat(mapping.toWireValue(zdt)).isSameAs(zdt);
    }

    // ── Round-trip ────────────────────────────────────────────────────

    @Test
    public void testDateRoundTrip() {
        var mapping = QueryDslTypeMapping.SQL_DATE;
        Date original = Date.valueOf("2024-07-01");
        var connId = (ZonedDateTime) mapping.toConnIdValue(original);
        var back = (Date) mapping.toWireValue(connId);
        assertThat(back).isEqualTo(original);
    }

    @Test
    public void testTimeRoundTrip() {
        var mapping = QueryDslTypeMapping.SQL_TIME;
        Time original = Time.valueOf("09:15:30");
        var connId = (String) mapping.toConnIdValue(original.toLocalTime());
        var back = (LocalTime) mapping.toWireValue(connId);
        assertThat(back).isEqualTo(original.toLocalTime());
    }
}
