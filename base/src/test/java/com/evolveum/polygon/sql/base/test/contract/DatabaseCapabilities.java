/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

/** Genuine database differences that affect shared connector assertions. */
public record DatabaseCapabilities(
        boolean external,
        boolean supportsSchemas,
        boolean supportsRemarks,
        boolean supportsNativeDefinitions,
        boolean supportsJdbcDefaults) {
}
