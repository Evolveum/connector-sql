package com.evolveum.polygon.sql.base.build.api;


import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class SqlObjectClassSchemaBuilderImpl extends BaseObjectClassDefinitionBuilder<
        SqlObjectClassSchemaBuilder,
        SqlObjectClassDefinition,
        SqlAttributeBuilder<SqlAttributeBuilder.Reference>,
        SqlAttributeBuilder.Reference,
        SqlAttributeBuilderImpl, SqlAttributeDefinition> implements SqlObjectClassSchemaBuilder {

    private DefinitionValue<String> schema = DefinitionValue.emptyDefault();
    private DefinitionValue<String> table;
    private Boolean onlyExplicitlyListed = false;
    private final Set<String> explicitRemoteNames = new LinkedHashSet<>();

    public SqlObjectClassSchemaBuilderImpl(SqlSchemaBuilderImpl restSchemaBuilder, DefinitionValue<String> name) {
        super(restSchemaBuilder, name);
        table = name.asDefault();
    }

    @Override
    protected SqlAttributeBuilderImpl newAttribute(DefinitionValue<String> def) {
        explicitRemoteNames.add(def.value());
        return new SqlAttributeBuilderImpl(this, def);
    }

    @Override
    public SqlObjectClassSchemaBuilder onlyExplicitlyListed(boolean value) {
        this.onlyExplicitlyListed = value;
        return this;
    }

    @Override
    public Boolean getOnlyExplicitlyListed() {
        return onlyExplicitlyListed;
    }

    /**
     * Checks if a column name has an explicit attribute definition.
     */
    public boolean hasExplicitRemoteName(String columnName) {
        return explicitRemoteNames.contains(columnName);
    }

    /**
     * Returns the set of explicitly defined attribute column names.
     */
    public Set<String> getExplicitRemoteNames() {
        return explicitRemoteNames;
    }

    @Override
    public SqlMapping sql() {
        return new SqlMapping() {
            @Override
            public void table(String name) {
                table(DefinitionValue.from(name, SourceLocation.capture()));
            }

            @Override
            public String schema() {
                return schema.value();
            }

            @Override
            public String table() {
                return table.value();
            }

            @Override
            public SqlMapping schema(DefinitionValue<String> detected) {
                schema = schema.moreSpecific(detected);
                return this;
            }

            @Override
            public SqlMapping table(DefinitionValue<String> value) {
                table = table.moreSpecific(value);
                return this;
            }
        };
    }

    @Override
    protected SqlObjectClassDefinition buildImpl(ObjectClassInfo connIdInfo,
                                                 Map<String, SqlAttributeDefinition> nativeAttrs,
                                                 Map<String, SqlAttributeDefinition> connIdAttrs) {

        if (!connIdAttrs.containsKey(Name.NAME)) {
            var uidAttribute = connIdAttrs.get(Uid.NAME);
            if (uidAttribute != null) {
                var attributeBuilder = newAttribute(DefinitionValue.defaultFrom(Name.NAME));
                attributeBuilder.emulated(DefinitionValue.detected(true));
                attributeBuilder.sql().column(uidAttribute.sql().column());
                attributeBuilder.sql().valueMapping(DefinitionValue.detected(uidAttribute.sql().sqlMapping()));
                var attribute = attributeBuilder.build();
                nativeAttrs.put(Name.NAME, attribute);
                connIdAttrs.put(Uid.NAME, attribute);
            }
        }

        var sql = new SqlSchemaBuilderImpl.SqlObjectClassMapping(schema, table);

        return  new SqlObjectClassDefinition(connIdInfo, nativeAttrs, connIdAttrs, sql);
    }


}
