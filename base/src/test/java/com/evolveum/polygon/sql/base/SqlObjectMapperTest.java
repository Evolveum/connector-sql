/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.build.api.SqlTypeSpecification;
import com.evolveum.polygon.sql.base.connection.SqlSchemaValueMapping;
import com.querydsl.core.types.Path;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlObjectMapperTest {

    private SqlObjectMapper mapper;

    private static final class StubConnector implements Connector {
        @Override public Configuration getConfiguration() { return null; }
        @Override public void init(Configuration configuration) { }
        @Override public void dispose() { }
    }

    @BeforeMethod
    public void setUp() {
        mapper = new SqlObjectMapper(objectClassDefinition());
    }

    @Test
    public void testSelectColumnsReturnsDefaultAttributes() {
        var selected = mapper.selectColumns(mapper.tablePath());

        assertThat(attributeNames(selected))
                .contains(Uid.NAME, Name.NAME, "display_name")
                .doesNotContain("secret");
    }

    private SqlObjectClassDefinition objectClassDefinition() {
        var schemaBuilder = new SqlSchemaBuilderImpl(StubConnector.class, ContextLookup.none());
        var objectClass = schemaBuilder.objectClass("account");
        objectClass.table("account");

        var uid = objectClass.attribute("id");
        uid.connId().name(Uid.NAME);
        uid.sql().type(SqlTypeSpecification.Mixin.INT);

        var displayName = objectClass.attribute("display_name");
        displayName.sql().type(SqlSchemaValueMapping.VARCHAR.asTypeSpecification());

        var secret = objectClass.attribute("secret");
        secret.returnedByDefault(false);
        secret.sql().type(SqlSchemaValueMapping.VARCHAR.asTypeSpecification());

        return schemaBuilder.build().objectClasses().stream()
                .findFirst()
                .orElseThrow();
    }

    private List<String> attributeNames(
            Map<SqlAttributeDefinition, Collection<Path<?>>> selected) {
        return selected.keySet().stream()
                .map(attribute -> attribute.connId().getName())
                .toList();
    }
}
