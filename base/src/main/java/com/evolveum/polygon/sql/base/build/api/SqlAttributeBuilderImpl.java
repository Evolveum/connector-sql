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
import com.evolveum.polygon.conndev.schema.ValueTypeOverrideMapping;
import com.evolveum.polygon.sql.base.build.spi.SpiSqlAttributeBuilder;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;

import java.util.ArrayList;
import java.util.List;

public class SqlAttributeBuilderImpl extends BaseAttributeBuilder<SqlAttributeBuilderImpl, SqlAttributeBuilder<SqlAttributeBuilder.Reference>, SqlAttributeBuilder.Reference, SqlAttributeDefinition> implements SqlAttributeBuilder.Reference {

    public SqlAttributeBuilderImpl(BaseObjectClassDefinitionBuilder restObjectClassBuilder, DefinitionValue<String> name) {
        super(restObjectClassBuilder, name);
    }

    private SqlMappingBuilder sqlMapping;

    @Override public SqlMappingBuilder sql() {
        if (sqlMapping == null) { sqlMapping = new SqlMappingBuilder(name.value()); }
        return sqlMapping;
    }

    @Override public SqlAttributeDefinition build() { return new SqlAttributeDefinition(this); }

    public class SqlMappingBuilder implements SqlMapping, AttributeProtocolMappingBuilder {

        private DefinitionValue<SqlTypeSpecification> type = DefinitionValue.emptyDefault();
        private DefinitionValue<String> column = DefinitionValue.emptyDefault();
        private DefinitionValue<Boolean> notNull = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> unique = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> primaryKey = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<Boolean> autoIncrement = DefinitionValue.DEFAULT_FALSE;
        private DefinitionValue<SqlValueMapping> valueMapping = DefinitionValue.emptyDefault();
        private final List<SqlAdditionalColumnDef> additionalColumns = new ArrayList<>();
        private SqlAttributeMapping override;

        public SqlMappingBuilder(String name) { this.column = DefinitionValue.defaultFrom(name); }

        @Override public SqlMapping name(String name) {
            var d = DefinitionValue.from(name, SourceLocation.capture());
            this.column = this.column.moreSpecific(d);
            return this;
        }

        @Override public SqlMapping column(DefinitionValue<String> name) {
            this.column = this.column.moreSpecific(name);
            return this;
        }

        @Override public SqlMapping type(DefinitionValue<SqlTypeSpecification> typeSpec) {
            this.type = this.type.moreSpecific(typeSpec);
            return this;
        }

        public SqlTypeSpecification VARCHAR(int size) { return DefaultSqlTypeSpecification.varcharType(size); }

        @Override public SqlMapping notNull(DefinitionValue<Boolean> notNull) { this.notNull = notNull; return this; }
        @Override public SqlMapping unique(DefinitionValue<Boolean> unique) { this.unique = unique; return this; }
        @Override public SqlMapping valueMapping(DefinitionValue<SqlValueMapping> d) { this.valueMapping = d; return this; }

        public SqlMapping primaryKey() { return primaryKey(true); }
        @Override public SqlMapping primaryKey(boolean value) { return primaryKey(DefinitionValue.from(value, SourceLocation.capture())); }
        public SqlMapping autoIncrement(boolean value) { return autoIncrement(DefinitionValue.from(value, SourceLocation.capture())); }
        @Override public SqlMapping autoIncrement(DefinitionValue<Boolean> value) { this.autoIncrement = value; return this; }
        @Override public SqlMapping primaryKey(DefinitionValue<Boolean> primaryKey) { this.primaryKey = primaryKey; return this; }

        @Override public DefinitionValue<String> column() { return column; }
        @Override public Class<?> suggestedConnIdType() { return valueMapping.value() != null ? valueMapping.value().connIdType() : Object.class; }

        public SpiSqlAttributeBuilder.SqlUIDMappingBuilder additionalColumns() { return new SqlUIDMappingBuilder(); }

        @Override
        public SqlAttributeMapping build() {
            if (this.override != null) {
                return this.override;
            }

            if (column.isEmpty() || (valueMapping.isEmpty() && type.isEmpty())) { return null; }
            var overrideMapping = connId().overrideMappingIfNeeded(this.valueMapping.value());
            var main = SqlAttributeMapping.singleColumn(column, this.valueMapping.value(), overrideMapping);
            if (additionalColumns.isEmpty()) {
                return main;
            }
            var extra = new ArrayList<SqlAttributeMapping.SingleColumn>();
            for (var a : additionalColumns) {
                var mapped = a.mapping() != null ? a.mapping() : SqlValueMapping.from(a.jdbcType());
                var override = ValueTypeOverrideMapping.of(String.class, mapped);
                extra.add(SqlAttributeMapping.singleColumn(
                        DefinitionValue.from(a.column(), SourceLocation.capture()),
                        mapped, override));
            }
            return SqlAttributeMapping.multiColumn(main, extra, SqlAttributeMapping.DEFAULT_DELIMITER);
        }

        private SqlMappingBuilder addExtra(String name, SqlValueMapping mapping, int jdbcType) {
            additionalColumns.add(new SqlAdditionalColumnDef(name, mapping, jdbcType));
            return this;
        }

        public void override(SqlAttributeMapping mapping) {
            this.override = mapping;
        }

        private record SqlAdditionalColumnDef(String column, SqlValueMapping mapping, int jdbcType) { }

        public class SqlUIDMappingBuilder implements SpiSqlAttributeBuilder.SqlUIDMappingBuilder {
            @Override
            public SqlMapping column(String name) { return addExtra(name, null, 0); }

            @Override
            public SqlMapping column(String name, SqlValueMapping mapping) {
                return addExtra(name, mapping, mapping.jdbcType() != null ? mapping.jdbcType().getVendorTypeNumber() : 0);
            }
        }
    }
}