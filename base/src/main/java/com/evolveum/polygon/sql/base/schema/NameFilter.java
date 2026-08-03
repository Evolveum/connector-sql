/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import java.util.regex.Pattern;

/**
 * Reusable include/exclude name filter for table or view discovery.
 *
 * <p>Name is accepted when all of the following hold:
 * <ol>
 *   <li>It does not match the exclusion pattern (if set).</li>
 *   <li>If an inclusion pattern is set, it matches the pattern.</li>
 * </ol>
 */
public final class NameFilter {

    private static final Pattern INCLUDE_ALL = Pattern.compile(".*");

    private final Pattern include;
    private final Pattern exclude;

    /**
     * Creates a NameFilter with the given include/exclude regex patterns.
     *
     * @param include regex of names to include (null or empty = all names)
     * @param exclude regex of names to exclude (null or empty = none excluded)
     */
    public NameFilter(String include, String exclude) {
        this.include = (include != null && !include.isEmpty()) ? Pattern.compile(include) : INCLUDE_ALL;
        this.exclude = (exclude != null && !exclude.isEmpty()) ? Pattern.compile(exclude) : null;
    }

    /**
     * Returns true if the name passes the filter rules.
     *
     * @param name the name to check
     * @return true if the name should be processed
     */
    public boolean passes(String name) {
        // 1. Exclusion check
        if (exclude != null && exclude.matcher(name).matches()) {
            return false;
        }
        // 2. Inclusion check
        if (!include.matcher(name).matches()) {
            return false;
        }
        return true;
    }
}