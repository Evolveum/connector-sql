/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.search.SqlRelatedJoinResolverSupport.JoinValues;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlRelatedJoinResolverSupportTest {

    @Test
    public void numericJoinEqualityIgnoresJdbcTypeAndDecimalScale() {
        var integral = new JoinValues(List.of(BigInteger.ONE, 2));
        var decimal = new JoinValues(List.of(new BigDecimal("1.00"), 2L));

        assertThat(integral).isEqualTo(decimal);
        assertThat(integral.hashCode()).isEqualTo(decimal.hashCode());
        assertThat(integral).isNotEqualTo(new JoinValues(List.of("1", 2)));
    }

    @Test
    public void nullInAnyJoinColumnMakesKeyIncomplete() {
        assertThat(new JoinValues(Arrays.asList(1, null)).isComplete()).isFalse();
        assertThat(new JoinValues(Arrays.asList(null, 1)).isComplete()).isFalse();
        assertThat(new JoinValues(List.of(1, 2)).isComplete()).isTrue();
    }
}
