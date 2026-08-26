/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.SqlDatabase;

/** Database lifecycle used by the cross-database connector contract. */
public interface SqlTestDatabase extends AutoCloseable {

    SqlDatabase database();

    DatabaseCapabilities capabilities();

    SqlConnectorConfiguration configuration(boolean developmentMode);

    void initializeSchema() throws Exception;

    @Override
    void close() throws Exception;
}
