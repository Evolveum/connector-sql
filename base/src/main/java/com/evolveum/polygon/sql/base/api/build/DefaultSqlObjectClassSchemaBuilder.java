package com.evolveum.polygon.sql.base.api.build;

import com.evolveum.polygon.conndev.build.ObjectClassSchemaBuilder;
import com.evolveum.polygon.conndev.build.ReferenceAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import org.identityconnectors.framework.common.objects.AttributeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultSqlObjectClassSchemaBuilder implements SqlObjectClassSchemaBuilder {

    private String objectClassName;
    private String description;
    private boolean embedded = false;
    private String tableName;
    private String uidAttribute;
    private final List<SqlAttributeBuilder> attributes = new ArrayList<>();

    @Override
    public ObjectClassSchemaBuilder description(String description) {
        this.description = description;
        return this;
    }

    @Override
    public ObjectClassSchemaBuilder embedded(boolean embedded) {
        this.embedded = embedded;
        return this;
    }

    @Override
    public SqlAttributeBuilder attribute(String name) {
        DefaultSqlAttributeBuilder attr = new DefaultSqlAttributeBuilder(name);
        attributes.add(attr);
        return attr;
    }

    @Override
    public SqlAttributeBuilder attribute(String name, Closure<?> closure) {
        DefaultSqlAttributeBuilder attr = new DefaultSqlAttributeBuilder(name);
        attributes.add(attr);
        GroovyClosures.callAndReturnDelegate(closure, attr);
        return attr;
    }

    @Override
    public ReferenceAttributeBuilder reference(String name) {
        return createReference();
    }

    @Override
    public ReferenceAttributeBuilder reference(String name, Closure<?> closure) {
        DefaultSqlReference ref = createReference();
        GroovyClosures.callAndReturnDelegate(closure, ref);
        return ref;
    }

    @Override
    public SqlObjectClassSchemaBuilder connIdAttribute(String connIdName, String attributeName) {
        if ("UID".equalsIgnoreCase(connIdName) || "uid".equalsIgnoreCase(connIdName)) {
            this.uidAttribute = attributeName;
        }
        return this;
    }

    @Override
    public SqlMapping sql() {
        return new DefaultSqlObjectSqlMapping();
    }

    public String getObjectClassName() {
        return objectClassName;
    }

    public void setObjectClassName(String objectClassName) {
        this.objectClassName = objectClassName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isEmbedded() {
        return embedded;
    }

    public void setEmbedded(boolean embedded) {
        this.embedded = embedded;
    }

    public String getTableName() {
        return tableName;
    }

    public String getUidAttribute() {
        return uidAttribute;
    }

    public List<SqlAttributeBuilder> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    private DefaultSqlReference createReference() {
        return new DefaultSqlReference();
    }

    public class DefaultSqlObjectSqlMapping implements SqlMapping {

        private String table;

        @Override
        public void table(String table) {
            this.table = table;
            DefaultSqlObjectClassSchemaBuilder.this.tableName = table;
        }

        public String table() {
            return table;
        }

        public String getTable() {
            return table;
        }
    }

    public class DefaultSqlReference implements ReferenceAttributeBuilder {

        @Override
        public ReferenceAttributeBuilder readable(boolean readable) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder required(boolean required) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder description(String description) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder returnedByDefault(boolean returnedByDefault) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder multiValued(boolean multiValued) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder creatable(boolean creatable) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder updatable(boolean updatable) {
            return this;
        }

        @Override
        public void emulated(boolean emulated) {
        }

        @Override
        public JsonMapping json() {
            return null;
        }

        @Override
        public JsonMapping json(Closure<?> closure) {
            return null;
        }

        @Override
        public ConnIdMapping connId() {
            return new DefaultReferenceConnIdMapping();
        }

        @Override
        public ReferenceAttributeBuilder protocolName(String protocolName) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder remoteName(String remoteName) {
            return this;
        }

        @Override
        public void complexType(String objectClass) {
        }

        @Override
        public ReferenceAttributeBuilder objectClass(String objectClass) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder subtype(String subtype) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder role(String role) {
            return this;
        }

        @Override
        public ReferenceAttributeBuilder role(AttributeInfo.RoleInReference role) {
            return this;
        }

        private class DefaultReferenceConnIdMapping implements ConnIdMapping {
            @Override
            public ConnIdMapping name(String name) {
                return this;
            }

            @Override
            public ConnIdMapping type(Class<?> connIdType) {
                return this;
            }
        }
    }
}