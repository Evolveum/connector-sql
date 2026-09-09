/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.groovy;

import com.evolveum.polygon.conndev.yaml.GroovyScriptCompiler;
import com.evolveum.polygon.conndev.yaml.YamlSchemaLoader;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.ManifestBasedConnector;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;
import com.evolveum.polygon.sql.base.groovy.impl.SqlOperationSupportBuilderImpl;
import com.evolveum.polygon.sql.base.yaml.YamlSqlOperationsLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extended (plural) SQL operations envelope is driven by the location-aware engine onto the live
 * {@link SqlOperationSupportBuilderImpl} — the declarative counterpart of the Groovy
 * {@code objectClass('Employee') { create { enabled false } }} DSL.
 */
public class SqlYamlOperationsLoaderTest {

    private SqlOperationSupportBuilderImpl newHandlerBuilder(SqlBaseContext context) {
        var schemaBuilder = new SqlSchemaBuilderImpl(ManifestBasedConnector.class, context);
        new YamlSchemaLoader(schemaBuilder).load("""
                objectClasses:
                  Employee:
                    attributes:
                      id:
                        connId:
                          name: __UID__
                        sql:
                          type: INT
                """);
        context.schema(schemaBuilder.build());
        return new SqlOperationSupportBuilderImpl(context);
    }

    @Test
    public void enabledFlagsBindFromYamlOpsDocument() {
        var context = new SqlBaseContext(new SqlConnectorConfiguration());
        var handlerBuilder = newHandlerBuilder(context);

        new YamlSqlOperationsLoader(handlerBuilder,
                new GroovyScriptCompiler(context.configuration().groovyContext()))
                .load(new StringReader("""
                        objectClasses:
                          Employee:
                            create:
                              enabled: false
                            update:
                              enabled: true
                        """), "test.yaml");

        var employee = (SqlObjectOperationBuilderImpl) handlerBuilder.objectClass("Employee");
        assertThat(employee.isCreateDisabled()).isTrue();
        assertThat(employee.isUpdateDisabled()).isFalse();
    }

    @Test
    public void unknownTopLevelKeyFailsFast() {
        var context = new SqlBaseContext(new SqlConnectorConfiguration());
        var handlerBuilder = newHandlerBuilder(context);

        var exception = Assert.expectThrows(IllegalArgumentException.class,
                () -> new YamlSqlOperationsLoader(handlerBuilder,
                        new GroovyScriptCompiler(context.configuration().groovyContext()))
                        .load(new StringReader("""
                                objectClass: Employee
                                """), "test.yaml"));
        assertThat(exception.getMessage()).contains("objectClass");
    }
}
