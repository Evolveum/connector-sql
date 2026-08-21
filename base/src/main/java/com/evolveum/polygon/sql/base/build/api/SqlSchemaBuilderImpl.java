/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.sql.base.schema.SqlSchemaDetector;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.sql.RelationalPathBase;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.*;

public class SqlSchemaBuilderImpl extends BaseSchemaBuilder<SqlSchemaBuilderImpl, SqlObjectClassSchemaBuilderImpl,
        SqlSchemaBuilder, SqlObjectClassSchemaBuilder, SqlObjectClassDefinition, SqlSchema> implements SqlSchemaBuilder {

    private Boolean onlyExplicitlyListed = false;

    public SqlSchemaBuilderImpl(Class<? extends Connector> connectorClass, ContextLookup context) {
        super(connectorClass, context);
    }

    @Override
    public SqlSchemaBuilder onlyExplicitlyListed(boolean value) {
        this.onlyExplicitlyListed = value;
        return this;
    }

    @Override
    public Boolean getOnlyExplicitlyListed() {
        return onlyExplicitlyListed;
    }

    public boolean isOnlyExplicitlyListed() {
        return Boolean.TRUE.equals(onlyExplicitlyListed);
    }

    public List<SqlObjectClassSchemaBuilderImpl> allObjectClassBuilders() {
        return new ArrayList<>(objectClasses.values());
    }

    /**
     * Returns the set of table references (schema+table) from all user-defined object classes
     * that have SQL schema/table mappings configured.
     */
    public Set<SqlSchemaDetector.TableRef> tableRefs() {
        Set<SqlSchemaDetector.TableRef> refs = new LinkedHashSet<>();
        for (SqlObjectClassSchemaBuilderImpl obc : objectClasses.values()) {
            var sql = obc.sql();
            var schema = sql.schema();
            var table = sql.table();
            if (table != null && !table.isEmpty()) {
                refs.add(new SqlSchemaDetector.TableRef(schema, table));
            }
        }
        return refs;
    }

    @Override
    protected SqlObjectClassSchemaBuilderImpl newObjectClass(DefinitionValue<String> name) {
        return new SqlObjectClassSchemaBuilderImpl(this, name);
    }

    @Override
    public RelationshipBuilder relationship(String name, Closure<?> closure) {
        return null;
    }

    @Override
    protected void initializeDummySchema() {
        if (Boolean.TRUE.equals(onlyExplicitlyListed)) {
            return;
        }
        var oc = objectClass("__Dummy");
        oc.attribute("id").connId().name(Uid.NAME).type(String.class);
        oc.attribute("name").connId().name(Name.NAME).type(String.class);
    }

    @Override
    protected SqlSchema newSchema(Schema connIdSchema, Map<ObjectClass, SqlObjectClassDefinition> objectClassMap) {
        return new SqlSchema(connIdSchema, objectClassMap);
    }

    /**
     * Bridges SQL-side table metadata and ConnId-side attribute definitions.
     */
    public static record SqlObjectClassMapping(
            DefinitionValue<String> schema,
            DefinitionValue<String> table) {

        public String getTableName() {
            return table.value();
        }


        public RelationalPathBase<?> pathAlias(String alias) {
            return new RelationalPathBase<>(Object.class, PathMetadataFactory.forVariable(alias), schema.value(), table.value());
        }
    }
}
