package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.build.api.RelationshipBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.querydsl.core.types.PathMetadataFactory;
import com.querydsl.sql.RelationalPathBase;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.Connector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlSchemaBuilderImpl extends BaseSchemaBuilder<SqlSchemaBuilderImpl, SqlObjectClassSchemaBuilderImpl,
        SqlSchemaBuilder, SqlObjectClassSchemaBuilder> implements SqlSchemaBuilder {

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
