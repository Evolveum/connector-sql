package com.evolveum.polygon.sql.base.build.api;


import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;

public class SqlObjectClassSchemaBuilderImpl extends BaseObjectClassDefinitionBuilder<
        SqlObjectClassSchemaBuilder,
        SqlObjectClassDefinition,
        SqlAttributeBuilder<SqlAttributeBuilder.Reference>,
        SqlAttributeBuilder.Reference,
        SqlAttributeBuilderImpl, SqlAttributeDefinition> implements SqlObjectClassSchemaBuilder {

    private DefinitionValue<String> schema = DefinitionValue.emptyDefault();
    private DefinitionValue<String> table;

    public SqlObjectClassSchemaBuilderImpl(SqlSchemaBuilderImpl restSchemaBuilder, DefinitionValue<String> name) {
        super(restSchemaBuilder, name);
        table = name.asDefault();
    }

    @Override
    protected SqlAttributeBuilderImpl newAttribute(DefinitionValue<String> def) {
        return new SqlAttributeBuilderImpl(this, def);
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
                schema  = schema.moreSpecific(detected);
                return this;
            }

            @Override
            public SqlMapping table(DefinitionValue<String> value) {
                table = table.moreSpecific(value);
                return this;
            }
        };
    }
}