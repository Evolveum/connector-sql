/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SqlTableAccessTest {

    @Test
    public void convertsNumericKeysToDestinationColumnType() {
        assertThat(table(SqlSchemaValueMapping.INTEGER).toWireValue("id", BigInteger.ONE)).isEqualTo(1);
        assertThat(table(SqlSchemaValueMapping.INTEGER).toWireValue("id", new BigDecimal("1.00"))).isEqualTo(1);
        assertThat(table(SqlSchemaValueMapping.BIGINT).toWireValue("id", 1)).isEqualTo(BigInteger.ONE);
        assertThat(table(SqlSchemaValueMapping.DECIMAL).toWireValue("id", 1))
                .isEqualTo(BigDecimal.ONE);
        assertThat(table(SqlSchemaValueMapping.SMALLINT).toWireValue("id", 1)).isEqualTo((short) 1);
        assertThat(table(SqlSchemaValueMapping.SMALLINT).toConnIdValue("id", BigInteger.ONE)).isEqualTo(1);
    }

    @Test
    public void rejectsFractionalAndOverflowingIntegerKeys() {
        var integer = table(SqlSchemaValueMapping.INTEGER);
        assertThatThrownBy(() -> integer.toWireValue("id", new BigDecimal("1.5")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> integer.toWireValue("id", new BigInteger("2147483648")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> table(SqlSchemaValueMapping.SMALLINT).toWireValue("id", 32768))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    public void convertsTimeUsingItsDeclaredValueMapping() {
        assertThat(table(SqlSchemaValueMapping.TIME).toWireValue("id", "14:30:45"))
                .isEqualTo(LocalTime.of(14, 30, 45));
    }

    private SqlTableAccess table(SqlSchemaValueMapping mapping) {
        var metadata = SqlTableInfo.builder().name("test_table")
                .addColumn(SqlColumnMeta.builder().name("id").valueMapping(mapping).build()).build();
        var context = new SqlBaseContext(new SqlConnectorConfiguration());
        context.setTableInfos(Map.of("test_table", metadata));
        return new SqlTableAccess(context, "test_table", "t");
    }
}
