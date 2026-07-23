/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link QueryDslTypeMapping} — QueryDSL Java type ↔ ConnId type conversions.
 */
@Test
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
        LocalTime lt = sqlTime.toLocalTime();
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
        ZonedDateTime connId = (ZonedDateTime) mapping.toConnIdValue(original);
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
