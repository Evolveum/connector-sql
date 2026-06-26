package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.build.AttributeBuilder;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

import java.util.ArrayList;
import java.util.List;

public class DefaultSqlAttributeBuilder implements SqlAttributeBuilder {

    private String name;
    private boolean readable = true;
    private boolean required = false;
    private String description;
    private boolean returnedByDefault = true;
    private boolean multiValued = false;
    private boolean creatable = true;
    private boolean updatable = true;
    private boolean emulated = false;
    private String protocolName;
    private String remoteName;
    private String complexType;
    private DefaultSqlAttributeMapping sqlMapping;
    private String connIdName;
    private Class<?> connIdType;
    private final List<String> attributeNames = new ArrayList<>();
    private Boolean primaryKey;
    private Boolean autoIncrement;

    public DefaultSqlAttributeBuilder() {
    }

    public DefaultSqlAttributeBuilder(String name) {
        this.name = name;
    }

    @Override
    public SqlAttributeBuilder readable(boolean readable) {
        this.readable = readable;
        return this;
    }

    @Override
    public SqlAttributeBuilder required(boolean required) {
        this.required = required;
        return this;
    }

    @Override
    public SqlAttributeBuilder description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public SqlAttributeBuilder returnedByDefault(boolean returnedByDefault) {
        this.returnedByDefault = returnedByDefault;
        return this;
    }

    @Override
    public SqlAttributeBuilder multiValued(boolean multiValued) {
        this.multiValued = multiValued;
        return this;
    }

    @Override
    public SqlAttributeBuilder creatable(boolean creatable) {
        this.creatable = creatable;
        return this;
    }

    @Override
    public SqlAttributeBuilder updatable(boolean updatable) {
        this.updatable = updatable;
        return this;
    }

    @Override
    public void emulated(boolean emulated) {
        this.emulated = emulated;
    }

    @Override
    public AttributeBuilder.JsonMapping json() {
        return null;
    }

    @Override
    public AttributeBuilder.JsonMapping json(@DelegatesTo(value = AttributeBuilder.JsonMapping.class, strategy = Closure.DELEGATE_ONLY) Closure<?> closure) {
        return null;
    }

    @Override
    public ConnIdMapping connId() {
        return new DefaultConnIdMapping();
    }

    @Override
    public SqlAttributeBuilder protocolName(String protocolName) {
        this.protocolName = protocolName;
        return this;
    }

    @Override
    public SqlAttributeBuilder remoteName(String remoteName) {
        this.remoteName = remoteName;
        return this;
    }

    @Override
    public void complexType(String objectClass) {
        this.complexType = objectClass;
    }

    @Override
    public SqlMapping sql() {
        if (sqlMapping == null) {
            sqlMapping = new DefaultSqlAttributeMapping();
        }
        return sqlMapping;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public boolean isReturnedByDefault() {
        return returnedByDefault;
    }

    public void setReturnedByDefault(boolean returnedByDefault) {
        this.returnedByDefault = returnedByDefault;
    }

    public void setConnIdName(String connIdName) {
        this.connIdName = connIdName;
    }

    public boolean isMultiValued() {
        return multiValued;
    }

    public boolean isCreatable() {
        return creatable;
    }

    public boolean isUpdatable() {
        return updatable;
    }

    public boolean isEmulated() {
        return emulated;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public String getRemoteName() {
        return remoteName;
    }

    public String getComplexType() {
        return complexType;
    }

    public DefaultSqlAttributeMapping getSqlMapping() {
        return sqlMapping;
    }

    public String getConnIdName() {
        return connIdName;
    }

    public Class<?> getConnIdType() {
        return connIdType;
    }

    public Boolean getPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(Boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public Boolean getAutoIncrement() {
        return autoIncrement;
    }

    public void setAutoIncrement(Boolean autoIncrement) {
        this.autoIncrement = autoIncrement;
    }

    public void addAttributeName(String attributeName) {
        this.attributeNames.add(attributeName);
    }

    public List<String> getAttributeNames() {
        return attributeNames;
    }

    public class DefaultConnIdMapping implements ConnIdMapping {

        @Override
        public ConnIdMapping name(String name) {
            DefaultSqlAttributeBuilder.this.connIdName = name;
            return this;
        }

        @Override
        public ConnIdMapping type(Class<?> connIdType) {
            DefaultSqlAttributeBuilder.this.connIdType = connIdType;
            return this;
        }
    }

    public class DefaultSqlAttributeMapping implements SqlMapping {

        private DefaultSqlTypeSpecification type;
        private String name;
        private boolean notNull;
        private boolean unique;

        @Override
        public SqlMapping name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public SqlMapping type(SqlTypeSpecification typeSpecification) {
            if (typeSpecification instanceof SqlTypeSpecification.Mixin) {
                // Groovy convenience value (INT, TIMESTAMP, etc. are null)
                // We ignore null values since they represent convenience access
            }
            return this;
        }

        @Override
        public SqlTypeSpecification VARCHAR(int size) {
            this.type = DefaultSqlTypeSpecification.varcharType(size);
            return DefaultSqlTypeSpecification.varcharType(size);
        }

        @Override
        public SqlMapping notNull(boolean notNull) {
            this.notNull = notNull;
            return this;
        }

        @Override
        public SqlMapping unique(boolean unique) {
            this.unique = unique;
            return this;
        }

        public DefaultSqlTypeSpecification getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public boolean isNotNull() {
            return notNull;
        }

        public boolean isUnique() {
            return unique;
        }

        // Groovy convenience methods - not in interface but usable in Groovy DSL
        public SqlMapping primaryKey() {
            DefaultSqlAttributeBuilder.this.setPrimaryKey(Boolean.TRUE);
            return this;
        }

        public SqlMapping autoIncrement() {
            DefaultSqlAttributeBuilder.this.setAutoIncrement(Boolean.TRUE);
            return this;
        }
    }
}