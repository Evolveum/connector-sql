/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.conndev.concepts.RetrievableContext;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.Map;

/** JDBC resources and native parent keys belonging to one coordinated write. */
final class SqlWriteContext implements RetrievableContext {

    private final SqlConnection connection;
    private final SqlWriteOperationSupport support;
    private final Collection<String> parentColumns;
    private Uid parentUid;
    private Map<String, Object> parentValues;

    SqlWriteContext(SqlConnection connection, SqlWriteOperationSupport support,
            Collection<String> parentColumns) {
        this.connection = connection;
        this.support = support;
        this.parentColumns = parentColumns;
    }

    SqlConnection connection() {
        return connection;
    }

    Map<String, Object> parentValues(Uid uid) {
        // Resolve after the primary write: create may generate the UID, and update
        // may change a non-UID join column. All child handlers then reuse these keys.
        if (parentValues == null || !uid.equals(parentUid)) {
            parentValues = support.parentColumnValues(connection, uid, parentColumns);
            parentUid = uid;
        }
        return parentValues;
    }
}
