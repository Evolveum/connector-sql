/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 */
package com.evolveum.polygon.sql.base.test.contract;

import org.testng.annotations.Test;

@Test(singleThreaded = true)
public class MySqlConnectorContractTest extends AbstractSqlConnectorContractTest {

    @Override
    protected SqlTestDatabase createDatabase() {
        return SqlTestDatabases.mysql();
    }
}
