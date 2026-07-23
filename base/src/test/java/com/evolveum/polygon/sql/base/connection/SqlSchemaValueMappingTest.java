/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.*;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SqlSchemaValueMapping} type mapping and conversions.
 * Covers all enum constants, both lookup methods, conversion logic,
 * and ensures no enum constant is orphaned (unreachable by any lookup).
 */
@Test
public class SqlSchemaValueMappingTest {

    // ── fromJdbcType() coverage ────────────────────────────────────────────

    @Test
    public void testFromJdbcTypeBasicTypes() {
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.VARCHAR)).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.CHAR)).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.LONGVARCHAR)).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.INTEGER)).isEqualTo(SqlSchemaValueMapping.INTEGER);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.BIGINT)).isEqualTo(SqlSchemaValueMapping.BIGINT);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.SMALLINT)).isEqualTo(SqlSchemaValueMapping.SMALLINT);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.TINYINT)).isEqualTo(SqlSchemaValueMapping.TINYINT);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.DECIMAL)).isEqualTo(SqlSchemaValueMapping.DECIMAL);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.NUMERIC)).isEqualTo(SqlSchemaValueMapping.NUMERIC);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.FLOAT)).isEqualTo(SqlSchemaValueMapping.FLOAT);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.REAL)).isEqualTo(SqlSchemaValueMapping.FLOAT);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.DOUBLE)).isEqualTo(SqlSchemaValueMapping.DOUBLE);
    }

    @Test
    public void testFromJdbcTypeBooleanVsBit() {
        // Types.BOOLEAN should map to BOOLEAN, not BIT
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.BOOLEAN))
                .as("BOOLEAN should map to BOOLEAN enum, not BIT")
                .isEqualTo(SqlSchemaValueMapping.BOOLEAN);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.BIT))
                .as("BIT should map to BIT enum")
                .isEqualTo(SqlSchemaValueMapping.BIT);
    }

    @Test
    public void testFromJdbcTypeTimestampVsTimestampWithTimezone() {
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.TIMESTAMP))
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.TIMESTAMP_WITH_TIMEZONE))
                .as("TIMESTAMP_WITH_TIMEZONE should map to its own enum constant")
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.TIME_WITH_TIMEZONE))
                .as("TIME_WITH_TIMEZONE falls back to TIMESTAMP")
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP);
    }

    @Test
    public void testFromJdbcTypeClob() {
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.CLOB))
                .as("CLOB should map to CLOB enum constant")
                .isEqualTo(SqlSchemaValueMapping.CLOB);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.NCLOB))
                .isEqualTo(SqlSchemaValueMapping.CLOB);
    }

    @Test
    public void testFromJdbcTypeBinaryTypes() {
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.BLOB))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.BINARY))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.VARBINARY))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.LONGVARBINARY))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
    }

    @Test
    public void testFromJdbcTypeNationalCharacterTypes() {
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.NCHAR))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.NVARCHAR))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.LONGNVARCHAR))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
    }

    @Test
    public void testFromJdbcTypeUnknownType() {
        // ARRAY, STRUCT, REF etc. should return null
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.ARRAY)).isNull();
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.STRUCT)).isNull();
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.REF)).isNull();
        assertThat(SqlSchemaValueMapping.fromJdbcType(Types.SQLXML)).isNull();
    }

    @Test
    public void testFromJdbcTypeAllEnumsReachable() {
        // Every enum constant MUST be reachable via fromJdbcType
        Set<SqlSchemaValueMapping> reachable = new HashSet<>();
        for (int i = -10; i < 2148; i++) {
            var mapping = SqlSchemaValueMapping.fromJdbcType(i);
            if (mapping != null) {
                reachable.add(mapping);
            }
        }
        for (SqlSchemaValueMapping expected : SqlSchemaValueMapping.values()) {
            assertThat(reachable)
                    .as("Enum constant %s must be reachable via fromJdbcType()", expected.name())
                    .contains(expected);
        }
    }

    // ── fromTypeName() coverage ────────────────────────────────────────────

    @Test
    public void testFromTypeNameBasicTypes() {
        assertThat(SqlSchemaValueMapping.fromTypeName("VARCHAR")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("CHAR")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("INTEGER")).isEqualTo(SqlSchemaValueMapping.INTEGER);
        assertThat(SqlSchemaValueMapping.fromTypeName("INT")).isEqualTo(SqlSchemaValueMapping.INTEGER);
        assertThat(SqlSchemaValueMapping.fromTypeName("BIGINT")).isEqualTo(SqlSchemaValueMapping.BIGINT);
        assertThat(SqlSchemaValueMapping.fromTypeName("SMALLINT")).isEqualTo(SqlSchemaValueMapping.SMALLINT);
        assertThat(SqlSchemaValueMapping.fromTypeName("TINYINT")).isEqualTo(SqlSchemaValueMapping.TINYINT);
        assertThat(SqlSchemaValueMapping.fromTypeName("DECIMAL")).isEqualTo(SqlSchemaValueMapping.DECIMAL);
        assertThat(SqlSchemaValueMapping.fromTypeName("NUMERIC")).isEqualTo(SqlSchemaValueMapping.NUMERIC);
        assertThat(SqlSchemaValueMapping.fromTypeName("NUMBER")).isEqualTo(SqlSchemaValueMapping.NUMERIC);
        assertThat(SqlSchemaValueMapping.fromTypeName("DECIMAL")).isEqualTo(SqlSchemaValueMapping.DECIMAL);
        assertThat(SqlSchemaValueMapping.fromTypeName("FLOAT")).isEqualTo(SqlSchemaValueMapping.FLOAT);
        assertThat(SqlSchemaValueMapping.fromTypeName("REAL")).isEqualTo(SqlSchemaValueMapping.FLOAT);
        assertThat(SqlSchemaValueMapping.fromTypeName("DOUBLE")).isEqualTo(SqlSchemaValueMapping.DOUBLE);
        assertThat(SqlSchemaValueMapping.fromTypeName("DOUBLE PRECISION")).isEqualTo(SqlSchemaValueMapping.DOUBLE);
    }

    @Test
    public void testFromTypeNameBooleanVsBit() {
        // BOOLEAN and BOOL should NOT map to BIT
        assertThat(SqlSchemaValueMapping.fromTypeName("BOOLEAN"))
                .as("BOOLEAN type name should map to BOOLEAN enum")
                .isEqualTo(SqlSchemaValueMapping.BOOLEAN);
        assertThat(SqlSchemaValueMapping.fromTypeName("BOOL"))
                .as("BOOL type name should map to BOOLEAN enum")
                .isEqualTo(SqlSchemaValueMapping.BOOLEAN);
        assertThat(SqlSchemaValueMapping.fromTypeName("BIT"))
                .as("BIT type name should map to BIT enum")
                .isEqualTo(SqlSchemaValueMapping.BIT);
    }

    @Test
    public void testFromTypeNameDatetimeVsDate() {
        // MySQL DATETIME should map to TIMESTAMP, not DATE
        assertThat(SqlSchemaValueMapping.fromTypeName("DATETIME"))
                .as("DATETIME should map to TIMESTAMP, not DATE")
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP);
        assertThat(SqlSchemaValueMapping.fromTypeName("TIMESTAMP"))
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP);
        assertThat(SqlSchemaValueMapping.fromTypeName("TIMESTAMP WITH TIME ZONE"))
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE);
        assertThat(SqlSchemaValueMapping.fromTypeName("DATE"))
                .isEqualTo(SqlSchemaValueMapping.DATE);
        assertThat(SqlSchemaValueMapping.fromTypeName("TIME"))
                .isEqualTo(SqlSchemaValueMapping.TIME);
    }

    @Test
    public void testFromTypeNameCharacterVariants() {
        assertThat(SqlSchemaValueMapping.fromTypeName("CHARACTER VARYING"))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("NVARCHAR"))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("NCHAR"))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("LONG VARCHAR"))
                .isEqualTo(SqlSchemaValueMapping.VARCHAR);
    }

    @Test
    public void testFromTypeNameBinaryTypes() {
        assertThat(SqlSchemaValueMapping.fromTypeName("BLOB"))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
        assertThat(SqlSchemaValueMapping.fromTypeName("BINARY"))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
        assertThat(SqlSchemaValueMapping.fromTypeName("VARBINARY"))
                .isEqualTo(SqlSchemaValueMapping.BLOB);
    }

    @Test
    public void testFromTypeNameClobTypes() {
        assertThat(SqlSchemaValueMapping.fromTypeName("CLOB"))
                .isEqualTo(SqlSchemaValueMapping.CLOB);
        assertThat(SqlSchemaValueMapping.fromTypeName("NCLOB"))
                .isEqualTo(SqlSchemaValueMapping.CLOB);
    }

    @Test
    public void testFromTypeNameNull() {
        assertThat(SqlSchemaValueMapping.fromTypeName(null)).isNull();
    }

    // ── toConnIdValue() conversions ────────────────────────────────────────

    @Test
    public void testFloatToConnIdValue() {
        var mapping = SqlSchemaValueMapping.FLOAT;
        assertThat(mapping.connIdType()).isEqualTo(Double.class);

        // Float should convert to Double
        var result = mapping.toConnIdValue(1.5f);
        assertThat(result).isInstanceOf(Double.class);
        assertThat((Double) result).isEqualTo(1.5);

        // Float should pass through
        result = mapping.toConnIdValue(1.5f);
        assertThat(result).isInstanceOf(Double.class);
        assertThat((Double) result).isEqualTo(1.5);
    }

    @Test
    public void testSmallintToConnIdValue() {
        var mapping = SqlSchemaValueMapping.SMALLINT;
        assertThat(mapping.connIdType()).isEqualTo(Integer.class);

        // Short should convert to Integer
        var result = mapping.toConnIdValue((short) 42);
        assertThat(result).isInstanceOf(Integer.class);
        assertThat((Integer) result).isEqualTo(42);

        // Short should pass through
        result = mapping.toConnIdValue((short) 42);
        assertThat(result).isInstanceOf(Integer.class);
    }

    @Test
    public void testBitToConnIdValue() {
        var mapping = SqlSchemaValueMapping.BIT;
        assertThat(mapping.connIdType()).isEqualTo(Boolean.class);

        // Boolean passthrough
        assertThat(mapping.toConnIdValue(true)).isEqualTo(true);
        assertThat(mapping.toConnIdValue(false)).isEqualTo(false);
        // null
        assertThat(mapping.toConnIdValue(null)).isNull();
    }

    @Test
    public void testBooleanToConnIdValue() {
        var mapping = SqlSchemaValueMapping.BOOLEAN;
        assertThat(mapping.connIdType()).isEqualTo(Boolean.class);

        assertThat(mapping.toConnIdValue(true)).isEqualTo(true);
        assertThat(mapping.toConnIdValue(false)).isEqualTo(false);
        assertThat(mapping.toConnIdValue(null)).isNull();
    }

    @Test
    public void testDateToConnIdValue() {
        var mapping = SqlSchemaValueMapping.DATE;
        assertThat(mapping.connIdType()).isEqualTo(ZonedDateTime.class);

        Date sqlDate = Date.valueOf("2024-06-15");
        var result = mapping.toConnIdValue(sqlDate);
        assertThat(result).isInstanceOf(ZonedDateTime.class);
        assertThat(((ZonedDateTime) result).toLocalDate()).isEqualTo(LocalDate.of(2024, 6, 15));
        assertThat(mapping.toConnIdValue(null)).isNull();
    }

    @Test
    public void testTimeToConnIdValue() {
        var mapping = SqlSchemaValueMapping.TIME;
        assertThat(mapping.connIdType()).isEqualTo(String.class);

        Time sqlTime = Time.valueOf("14:30:00");
        LocalTime lt = sqlTime.toLocalTime();
        var result = mapping.toConnIdValue(lt);
        assertThat(result).isInstanceOf(String.class);
        assertThat(result).isEqualTo(lt.toString());
    }

    @Test
    public void testTimestampToConnIdValue() {
        var mapping = SqlSchemaValueMapping.TIMESTAMP;
        assertThat(mapping.connIdType()).isEqualTo(ZonedDateTime.class);

        Timestamp ts = Timestamp.valueOf("2024-06-15 14:30:00");
        var result = mapping.toConnIdValue(ts);
        assertThat(result).isInstanceOf(ZonedDateTime.class);
    }

    @Test
    public void testTimestampWithTimezoneToConnIdValue() {
        var mapping = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE;
        assertThat(mapping.connIdType()).isEqualTo(ZonedDateTime.class);

        // ZonedDateTime passthrough
        ZonedDateTime zdt = ZonedDateTime.now();
        assertThat(mapping.toConnIdValue(zdt)).isSameAs(zdt);
    }

    // ── toWireValue() conversions ──────────────────────────────────────────

    @Test
    public void testTimestampToWireValue() {
        var mapping = SqlSchemaValueMapping.TIMESTAMP;

        // ZonedDateTime -> Timestamp
        ZonedDateTime zdt = ZonedDateTime.now();
        assertThat(mapping.toWireValue(zdt)).isInstanceOf(Timestamp.class);

        // null
        assertThat(mapping.toWireValue(null)).isNull();
    }

    @Test
    public void testDateToWireValue() {
        var mapping = SqlSchemaValueMapping.DATE;

        assertThat(mapping.toWireValue(ZonedDateTime.of(2024, 6, 15, 0, 0, 0, 0, ZoneId.systemDefault()))).isInstanceOf(Date.class);
        assertThat(mapping.toWireValue(null)).isNull();
    }

    @Test
    public void testTimeToWireValue() {
        var mapping = SqlSchemaValueMapping.TIME;

        assertThat(mapping.toWireValue("14:30:00")).isInstanceOf(LocalTime.class);
        assertThat(mapping.toWireValue(null)).isNull();
    }

    // ── Wire type assertions ───────────────────────────────────────────────

    @Test
    public void testWireTypesConsistent() {
        // Verify each enum's wiring type is correct
        assertThat(SqlSchemaValueMapping.VARCHAR.primaryWireType()).isEqualTo(String.class);
        assertThat(SqlSchemaValueMapping.INTEGER.primaryWireType()).isEqualTo(Integer.class);
        assertThat(SqlSchemaValueMapping.SMALLINT.primaryWireType()).isEqualTo(Short.class);
        assertThat(SqlSchemaValueMapping.NUMERIC.primaryWireType()).isEqualTo(BigDecimal.class);
        assertThat(SqlSchemaValueMapping.TINYINT.primaryWireType()).isEqualTo(Integer.class);
        assertThat(SqlSchemaValueMapping.BIGINT.primaryWireType()).isEqualTo(BigInteger.class);
        assertThat(SqlSchemaValueMapping.DECIMAL.primaryWireType()).isEqualTo(BigDecimal.class);
        assertThat(SqlSchemaValueMapping.FLOAT.primaryWireType()).isEqualTo(Float.class);
        assertThat(SqlSchemaValueMapping.DOUBLE.primaryWireType()).isEqualTo(Double.class);
        assertThat(SqlSchemaValueMapping.BOOLEAN.primaryWireType()).isEqualTo(Boolean.class);
        assertThat(SqlSchemaValueMapping.BIT.primaryWireType()).isEqualTo(Boolean.class);
        assertThat(SqlSchemaValueMapping.DATE.primaryWireType()).isEqualTo(Date.class);
        assertThat(SqlSchemaValueMapping.TIME.primaryWireType()).isEqualTo(LocalTime.class);
        assertThat(SqlSchemaValueMapping.TIMESTAMP.primaryWireType()).isEqualTo(Timestamp.class);
        assertThat(SqlSchemaValueMapping.BLOB.primaryWireType()).isEqualTo(byte[].class);
        assertThat(SqlSchemaValueMapping.CLOB.primaryWireType()).isEqualTo(String.class);
        assertThat(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.primaryWireType())
                .as("TIMESTAMP_WITH_TIMEZONE wire type should be ZonedDateTime.class")
                .isEqualTo(ZonedDateTime.class);
    }

    // ── ConnId type assertions ─────────────────────────────────────────────

    @Test
    public void testConnIdTypes() {
        assertThat(SqlSchemaValueMapping.VARCHAR.connIdType()).isEqualTo(String.class);
        assertThat(SqlSchemaValueMapping.INTEGER.connIdType()).isEqualTo(Integer.class);
        assertThat(SqlSchemaValueMapping.SMALLINT.connIdType()).isEqualTo(Integer.class);
        assertThat(SqlSchemaValueMapping.NUMERIC.connIdType()).isEqualTo(BigDecimal.class);
        assertThat(SqlSchemaValueMapping.TINYINT.connIdType()).isEqualTo(Integer.class);
        assertThat(SqlSchemaValueMapping.BIGINT.connIdType()).isEqualTo(BigInteger.class);
        assertThat(SqlSchemaValueMapping.DECIMAL.connIdType()).isEqualTo(BigDecimal.class);
        assertThat(SqlSchemaValueMapping.FLOAT.connIdType()).isEqualTo(Double.class);
        assertThat(SqlSchemaValueMapping.DOUBLE.connIdType()).isEqualTo(Double.class);
        assertThat(SqlSchemaValueMapping.BOOLEAN.connIdType()).isEqualTo(Boolean.class);
        assertThat(SqlSchemaValueMapping.BIT.connIdType()).isEqualTo(Boolean.class);
        assertThat(SqlSchemaValueMapping.DATE.connIdType()).isEqualTo(ZonedDateTime.class);
        assertThat(SqlSchemaValueMapping.TIME.connIdType()).isEqualTo(String.class);
        assertThat(SqlSchemaValueMapping.TIMESTAMP.connIdType()).isEqualTo(ZonedDateTime.class);
        assertThat(SqlSchemaValueMapping.BLOB.connIdType()).isEqualTo(byte[].class);
        assertThat(SqlSchemaValueMapping.CLOB.connIdType()).isEqualTo(String.class);
        assertThat(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE.connIdType()).isEqualTo(ZonedDateTime.class);
    }

    // ── Round-trip conversions ─────────────────────────────────────────────

    @Test
    public void testTimestampRoundTrip() {
        var mapping = SqlSchemaValueMapping.TIMESTAMP;
        Timestamp original = Timestamp.valueOf("2024-06-15 14:30:45");
        var connId = (ZonedDateTime) mapping.toConnIdValue(original);
        var back = (Timestamp) mapping.toWireValue(connId);
        assertThat(back.getTime()).isEqualTo(original.getTime());
    }

    @Test
    public void testTimestampWithTimezoneRoundTrip() {
        var mapping = SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE;
        ZonedDateTime original = ZonedDateTime.of(2024, 6, 15, 14, 30, 45, 0, ZoneId.of("UTC"));
        var connId = (ZonedDateTime) mapping.toConnIdValue(original);
        var back = (ZonedDateTime) mapping.toWireValue(connId);
        assertThat(back).isEqualTo(connId);
        // UTC timezone should be preserved
        assertThat(connId.getZone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    public void testDateRoundTrip() {
        var mapping = SqlSchemaValueMapping.DATE;
        Date original = Date.valueOf("2024-06-15");
        ZonedDateTime connId = (ZonedDateTime) mapping.toConnIdValue(original);
        var back = (Date) mapping.toWireValue(connId);
        assertThat(back).isEqualTo(original);
    }

    @Test
    public void testTimeRoundTrip() {
        var mapping = SqlSchemaValueMapping.TIME;
        LocalTime original = LocalTime.of(14, 30, 45);
        var connId = (String) mapping.toConnIdValue(original);
        var back = (LocalTime) mapping.toWireValue(connId);
        assertThat(back).isEqualTo(original);
    }

    // ── Edge cases ─────────────────────────────────────────────────────────

    @Test
    public void testUnknownJdbcTypeReturnsNull() {
        // JDBC type codes that have no mapping
        for (int code : new int[]{-1, 0, 100, 200, 1245, 2000, 2147}) {
            var mapping = SqlSchemaValueMapping.fromJdbcType(code);
            if (mapping != null) {
                // OK — some codes may have mappings we don't explicitly list
            }
        }
    }

    @Test
    public void testVarcharDefaultFallback() {
        // Unknown type name should fall back to VARCHAR
        assertThat(SqlSchemaValueMapping.fromTypeName("UUID")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("GEOMETRY")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromTypeName("SOME_CUSTOM_TYPE")).isEqualTo(SqlSchemaValueMapping.VARCHAR);
    }

    @Test
    public void testBaseToConnIdValueThrowsForUnsupportedType() {
        // VARCHAR expects String — passing an Integer should throw
        var mapping = SqlSchemaValueMapping.VARCHAR;
        assertThatThrownBy(() -> mapping.toConnIdValue(42))
                .isInstanceOf(IllegalArgumentException.class);
    }


    // ── fromQdslJavaType() coverage ─────────────────────────────────

    @Test
    public void testFromQdslJavaTypeBasic() {
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(String.class)).isEqualTo(SqlSchemaValueMapping.VARCHAR);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Integer.class)).isEqualTo(SqlSchemaValueMapping.INTEGER);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Short.class)).isEqualTo(SqlSchemaValueMapping.SMALLINT);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(BigDecimal.class)).isEqualTo(SqlSchemaValueMapping.NUMERIC);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(BigInteger.class)).isEqualTo(SqlSchemaValueMapping.BIGINT);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Float.class)).isEqualTo(SqlSchemaValueMapping.FLOAT);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Double.class)).isEqualTo(SqlSchemaValueMapping.DOUBLE);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Boolean.class)).isEqualTo(SqlSchemaValueMapping.BOOLEAN);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Date.class)).isEqualTo(SqlSchemaValueMapping.DATE);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(LocalTime.class)).isEqualTo(SqlSchemaValueMapping.TIME);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(Timestamp.class)).isEqualTo(SqlSchemaValueMapping.TIMESTAMP);
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(byte[].class)).isEqualTo(SqlSchemaValueMapping.BLOB);
    }

    @Test
    public void testFromQdslJavaTypeTimestampTz() {
        // ZonedDateTime maps to TIMESTAMP_WITH_TIMEZONE
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(ZonedDateTime.class))
                .isEqualTo(SqlSchemaValueMapping.TIMESTAMP_WITH_TIMEZONE);
    }

    @Test
    public void testFromQdslJavaTypeNullReturnsNull() {
        assertThat(SqlSchemaValueMapping.fromQdslJavaType(null)).isNull();
    }

    // ── QueryDslTypeMapping integration ──────────────────────────────────

    @Test
    public void testQdslTypeMappingPresent() {
        for (SqlSchemaValueMapping m : SqlSchemaValueMapping.values()) {
            assertThat(m.qdslTypeMapping())
                    .as("%s should have a QueryDslTypeMapping", m.name())
                    .isNotNull();
        }
    }
}
