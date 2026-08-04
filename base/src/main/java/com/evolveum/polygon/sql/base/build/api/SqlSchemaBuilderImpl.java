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
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SqlSchemaBuilderImpl extends BaseSchemaBuilder<SqlSchemaBuilderImpl, SqlObjectClassSchemaBuilderImpl,
        SqlSchemaBuilder, SqlObjectClassSchemaBuilder> implements SqlSchemaBuilder {

    private Boolean onlyExplicitlyListed = false;
    private final List<ObjectClassInfo> additionalObjectClasses = new ArrayList<>();

    public SqlSchemaBuilderImpl(Class<? extends Connector> connectorClass, ContextLookup context) {
        super(connectorClass, context);
    }

    /**
     * Adds a ready-made ConnId object class (e.g. the shared conndev dev object classes defined in
     * {@code ConnDevSchema}, or SQL's own {@code sql} protocol-specific block) to the schema, alongside
     * the mapped object classes.
     */
    public SqlSchemaBuilderImpl defineObjectClass(ObjectClassInfo objectClass) {
        additionalObjectClasses.add(objectClass);
        return this;
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
            String schema = sql.schema();
            String table = sql.table();
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
    public SqlSchema build() {
        if (objectClasses.isEmpty()) {
            if (!Boolean.TRUE.equals(onlyExplicitlyListed)) {
                @SuppressWarnings("unchecked")
                var oc = (SqlObjectClassSchemaBuilderImpl) objectClass("__Dummy");
                oc.attribute("id").connId().name(Uid.NAME).type(String.class);
                oc.attribute("name").connId().name(Name.NAME).type(String.class);
            }
        }

        var freshSchemaBuilder = new SchemaBuilder(connectorClass);
        Map<ObjectClass, SqlObjectClassDefinition> sqlObjectClassMap = new HashMap<>();

        for (SqlObjectClassSchemaBuilderImpl obc : objectClasses.values()) {
            var def = obc.build();
            freshSchemaBuilder.defineObjectClass(def.connId());
            sqlObjectClassMap.put(def.objectClass(), def);
        }
        for (var info : additionalObjectClasses) {
            freshSchemaBuilder.defineObjectClass(info);
        }

        var connIdSchema = freshSchemaBuilder.build();
        return new SqlSchema(connIdSchema, sqlObjectClassMap);
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
