/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.test.SqlIntegrationTestBase;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Integration test for build-phase validation of attribute names referenced from
 * custom search scripts: an unknown attribute must fail with a descriptive
 * {@link ConfigurationException} (naming the attribute, the object class and the
 * available attributes) instead of surfacing later as a {@code NullPointerException}.
 */
@Test(singleThreaded = true)
public class CustomSearchFilterValidationTest
        extends SqlIntegrationTestBase<CustomSearchFilterValidationTest.TestConnector> {

    protected static class TestConnector extends DefaultTestConnector {
        protected TestConnector() {
            super(GROOVY_HANDLER_SCRIPT);
        }
    }

    private static final String GROOVY_HANDLER_SCRIPT = """
            objectClass("USERS") {
                search {
                    custom {
                        supportedFilter(attribute("nonexistent").eq().anySingleValue())
                    }
                }
            }
            """;

    @Override
    protected String schemaSql() {
        return """
                DROP TABLE IF EXISTS users CASCADE;

                CREATE TABLE users (
                    id       INT PRIMARY KEY AUTO_INCREMENT,
                    username VARCHAR(255) NOT NULL
                );
                """;
    }

    @Override
    protected String dataSql() {
        return "INSERT INTO users (username) VALUES ('john.doe');";
    }

    @Override
    protected void initConnector() {
        connector = new TestConnector();
        connector.init(defaultConfig());
    }

    @Test
    public void testCustomSearchScriptWithUnknownFilterAttributeFails() {
        var failure = catchThrowable(() -> search("users", null));

        assertThat(failure).isNotNull();
        var cause = firstCause(failure, ConfigurationException.class);
        assertThat(cause).isNotNull();
        assertThat(cause.getMessage())
                .contains("Attribute 'nonexistent' not found in object class 'USERS'")
                .contains("when defining a custom search filter")
                .contains("Available attributes")
                .contains("USERNAME");
    }

    private static Throwable firstCause(Throwable throwable, Class<? extends Throwable> type) {
        for (var cause = throwable; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return cause;
            }
        }
        return null;
    }
}
