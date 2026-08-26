/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import org.testng.SkipException;

/** Availability policy for the opt-in external-database test modes. */
public final class ExternalDatabaseTestSupport {

    private ExternalDatabaseTestSupport() {
    }

    public static <T> T connect(String database, ThrowingSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception e) {
            if (Boolean.getBoolean("available.database.tests")
                    && !Boolean.getBoolean("all.database.tests")) {
                throw new SkipException(database + " is unavailable: " + rootMessage(e), e);
            }
            throw e;
        }
    }

    private static String rootMessage(Throwable throwable) {
        var current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
