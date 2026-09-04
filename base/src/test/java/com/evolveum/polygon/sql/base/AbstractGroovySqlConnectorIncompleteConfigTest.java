/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.sql.base.dev.SqlDevelopmentMode;
import com.evolveum.polygon.sql.base.test.TestConnectors;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.testng.annotations.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A wizard-fresh testing resource has a repository OID before the user has typed any
 * credentials into it. midPoint's "complete resource" step calls {@code schema()} on such a
 * resource anyway (to derive capabilities), so {@code schema()} must degrade to the locally
 * defined schema instead of trying to connect.
 */
@Test(singleThreaded = true)
public class AbstractGroovySqlConnectorIncompleteConfigTest {

    @Test
    public void schemaWithMissingCredentialsDoesNotConnect() {
        var config = new SqlConnectorConfiguration();
        // jdbcUrl / username / password intentionally left unset - isComplete() == false

        var connector = TestConnectors.of();
        connector.init(config);

        assertThatCode(connector::schema).doesNotThrowAnyException();
    }

    @Test
    public void schemaWithMissingCredentialsInDevelopmentModeExposesConnDevMetadata() {
        var config = new SqlConnectorConfiguration();
        config.setDevelopmentMode(true);

        var connector = TestConnectors.of();
        connector.init(config);

        var schema = connector.schema();

        List<String> types = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .collect(Collectors.toList());

        assertThat(types).contains(ConnDevObjectClass.OBJECT_CLASS_NAME, SqlDevelopmentMode.TABLE_OC_NAME);
    }
}
