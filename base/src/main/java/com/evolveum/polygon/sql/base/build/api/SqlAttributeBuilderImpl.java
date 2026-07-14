/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.AttributeProtocolMappingBuilder;
import com.evolveum.polygon.conndev.schema.BaseAttributeBuilder;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;

public class SqlAttributeBuilderImpl extends BaseAttributeBuilder<SqlAttributeBuilderImpl, SqlAttributeBuilder<SqlAttributeBuilder.Reference>, SqlAttributeBuilder.Reference, SqlAttributeDefinition> implements SqlAttributeBuilder.Reference {

    public SqlAttributeBuilderImpl(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        super(restObjectClassBuilder, name);
    }

    private SqlMappingBuilder sqlMapping;

    @Override
    public SqlMappingBuilder sql() {
        if (sqlMapping == null) {
            sqlMapping = new SqlMappingBuilder(name.value());
        }
        return sqlMapping;
    }

    @Override
    public SqlAttributeDefinition build() {
        // Create SqlAttributeDefinition directly instead of calling super (which creates BaseAttributeDefinition)
        return new SqlAttributeDefinition(this);
    }

    public class SqlMappingBuilder implements SqlMapping, AttributeProtocolMappingBuilder {

        private DefinitionValue<SqlTypeSpecification> type = DefinitionValue.emptyDefault();
        private DefinitionValue<String> column = DefinitionValue.emptyDefault();
        private DefinitionValue<Boolean> notNull = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> unique = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> primaryKey = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> autoIncrement =  DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<SqlValueMapping> valueMapping = DefinitionValue.emptyDefault();

        public SqlMappingBuilder(String name) {
            this.column = DefinitionValue.defaultFrom(name);
        }

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

        @Override
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
        public SqlMapping valueMapping(DefinitionValue<SqlValueMapping> detected) {
            this.valueMapping = this.valueMapping.moreSpecific(detected);
            return this;
        }

        @Override
        public SqlMapping primaryKey(DefinitionValue<Boolean> primaryKey) {
            this.primaryKey = this.primaryKey.moreSpecific(primaryKey);
            return this;
        }

        @Override
        public DefinitionValue<String> column() {
            return column;
        }

        @Override
        public Class<?> suggestedConnIdType() {
            return valueMapping.value().connIdType();
        }

        public SqlAttributeMapping build() {
            if (column.isEmpty() || (valueMapping.isEmpty() && type.isEmpty())) {
                return null;
            }

            var overrideMapping = connId().overrideMappingIfNeeded(this.valueMapping.value());
            return new SqlAttributeMapping(column, this.valueMapping.value(), overrideMapping);
        }
    }
}