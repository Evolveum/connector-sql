package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;

public class SqlAttributeBuilderImpl extends BaseAttributeBuilder<SqlAttributeBuilderImpl, SqlAttributeBuilder<SqlAttributeBuilder.Reference>, SqlAttributeBuilder.Reference, BaseAttributeDefinition> implements SqlAttributeBuilder.Reference {

    public SqlAttributeBuilderImpl(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        super(restObjectClassBuilder, name);
    }

    private final DefaultSqlAttributeMapping sqlMapping = new DefaultSqlAttributeMapping();

    @Override
    public SqlMapping sql() {
        return sqlMapping;
    }

    public class DefaultSqlAttributeMapping implements SqlMapping {

        private DefinitionValue<SqlTypeSpecification> type = DefinitionValue.emptyDefault();
        private DefinitionValue<String> column;
        private DefinitionValue<Boolean> notNull = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> unique = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> primaryKey = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> autoIncrement =  DefinitionValue.DEFAULT_FALSE;

        @Override
        public SqlMapping name(String name) {
            var nameDef  = DefinitionValue.from(name, SourceLocation.capture());
            this.column = this.column.moreSpecific(nameDef);
            return this;
        }

        @Override
        public SqlMapping column(DefinitionValue<String> name) {
            this.column = this.column.moreSpecific(name);
            return this;
        }

        public SqlMapping type(DefinitionValue<SqlTypeSpecification> typeSpecification) {
            this.type = this.type.moreSpecific(typeSpecification);
            return this;
        }

        @Override
        public SqlTypeSpecification VARCHAR(int size) {
            return DefaultSqlTypeSpecification.varcharType(size);
        }

        @Override
        public SqlMapping notNull(DefinitionValue<Boolean> notNull) {
            this.notNull = this.notNull.moreSpecific(notNull);
            return this;
        }

        @Override
        public SqlMapping unique(DefinitionValue<Boolean> unique) {
            this.unique = this.unique.moreSpecific(unique);
            return this;
        }


        // Groovy convenience methods - not in interface but usable in Groovy DSL
        public SqlMapping primaryKey() {
            return primaryKey(true);
        }

        @Override
        public SqlMapping primaryKey(boolean value) {
            return primaryKey(DefinitionValue.from(value, SourceLocation.capture()));
        }

        @Override
        public SqlMapping autoIncrement(boolean value) {
            return autoIncrement(DefinitionValue.from(value, SourceLocation.capture()));
        }

        @Override
        public SqlMapping autoIncrement(DefinitionValue<Boolean> value) {
            this.autoIncrement = this.autoIncrement.moreSpecific(value);
            return this;
        }

        @Override
        public SqlMapping primaryKey(DefinitionValue<Boolean> primaryKey) {
            this.primaryKey = this.primaryKey.moreSpecific(primaryKey);
            return this;
        }
    }
}