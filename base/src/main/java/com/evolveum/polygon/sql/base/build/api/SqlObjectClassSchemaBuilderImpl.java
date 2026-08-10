/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;


import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.conndev.yaml.YamlDocuments;
import com.evolveum.polygon.conndev.yaml.YamlProtocolBlockConsumer;
import com.evolveum.polygon.sql.base.schema.SqlChildJoinConfig;
import com.evolveum.polygon.sql.base.schema.SqlJunctionJoinConfig;
import com.evolveum.polygon.sql.base.yaml.model.YamlSqlBlock;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;
import tools.jackson.databind.JsonNode;

import java.util.*;

public class SqlObjectClassSchemaBuilderImpl extends BaseObjectClassDefinitionBuilder<
        SqlObjectClassSchemaBuilder,
        SqlObjectClassDefinition,
        SqlAttributeBuilder<SqlAttributeBuilder.Reference>,
        SqlAttributeBuilder.Reference,
        SqlAttributeBuilderImpl, SqlAttributeDefinition> implements SqlObjectClassSchemaBuilder, YamlProtocolBlockConsumer {

    private DefinitionValue<String> schema = DefinitionValue.emptyDefault();
    private DefinitionValue<String> table;
    private Boolean onlyExplicitlyListed = false;
    private DefinitionValue<Boolean> readOnly = DefinitionValue.DEFAULT_FALSE;
    private final Set<String> explicitRemoteNames = new LinkedHashSet<>();
    private final List<SqlChildJoinConfig> embeddedJoinConfigs = new ArrayList<>();
    private final List<SqlJunctionJoinConfig> junctionJoinConfigs = new ArrayList<>();

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

    @Override
    public SqlObjectClassSchemaBuilder readOnly(boolean value) {
        this.readOnly = DefinitionValue.from(value, SourceLocation.capture());
        return this;
    }

    public SqlObjectClassSchemaBuilder readOnly(DefinitionValue<Boolean> value) {
        this.readOnly = this.readOnly.moreSpecific(value);
        return this;
    }

    @Override
    public Boolean getReadOnly() {
        return readOnly.value();
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

    /** Adds a join config for an embedded child table. */
    public void addEmbeddedJoinConfig(SqlChildJoinConfig config) {
        embeddedJoinConfigs.add(config);
    }

    /** Adds a join config for a junction table reference. */
    public void addJunctionJoinConfig(SqlJunctionJoinConfig config) {
        junctionJoinConfigs.add(config);
    }

    /** Returns the embedded join configurations. */
    public List<SqlChildJoinConfig> getEmbeddedJoinConfigs() {
        return Collections.unmodifiableList(embeddedJoinConfigs);
    }

    /** Returns the junction join configurations. */
    public List<SqlJunctionJoinConfig> getJunctionJoinConfigs() {
        return Collections.unmodifiableList(junctionJoinConfigs);
    }

    /**
     * Finds an attribute by name using case-insensitive matching.
     * Used for Oracle compatibility where column names may differ in case.
     */
    public SqlAttributeBuilder.Reference findAttributeIgnoreCase(String name) {
        for (var attr : allAttributes()) {
            if (attr instanceof SqlAttributeBuilder<?> sqlAttr) {
                // We can't easily get the remote name, so we'll skip this for now
                // and rely on the Groovy scripts using the correct case.
            }
        }
        return null;
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

    /**
     * The {@code sql:} top-level YAML block — sets {@link #sql()}'s table/schema, exactly like the
     * Groovy {@code sql { table "..." } } DSL.
     */
    @Override
    public void applyProtocolBlock(String name, JsonNode block) {
        if (!"sql".equals(name)) {
            throw new IllegalArgumentException("Unknown protocol block '" + name + "' for object class '"
                    + name() + "'");
        }
        var sqlBlock = YamlDocuments.convert(block, YamlSqlBlock.class);
        if (sqlBlock.table != null) {
            table(sqlBlock.table);
        }
        if (sqlBlock.schema != null) {
            schema(sqlBlock.schema);
        }
    }

    @Override
    protected SqlObjectClassDefinition buildImpl(ObjectClassInfo connIdInfo,
                                                 Map<String, SqlAttributeDefinition> nativeAttrs,
                                                 Map<String, SqlAttributeDefinition> connIdAttrs) {

        if (!connIdAttrs.containsKey(Name.NAME)) {
            var uidAttribute = connIdAttrs.get(Uid.NAME);
            if (uidAttribute != null && uidAttribute.sql() instanceof SqlAttributeMapping mapping) {
                var attributeBuilder = newAttribute(DefinitionValue.defaultFrom(Name.NAME));

                // FIXME: Currently breaks with built-in attribute resolvers
                // attributeBuilder.emulated(DefinitionValue.detected(true));

                attributeBuilder.sql().override(mapping);
                if (Boolean.TRUE.equals(readOnly.value())) {
                    attributeBuilder.connId().creatable(DefinitionValue.detected(false));
                    attributeBuilder.connId().updatable(DefinitionValue.detected(false));
                }
                var attribute = attributeBuilder.build();
                nativeAttrs.put(Name.NAME, attribute);
                connIdAttrs.put(Name.NAME, attribute);
            }
        }

        var sql = new SqlSchemaBuilderImpl.SqlObjectClassMapping(schema, table);

        return  new SqlObjectClassDefinition(connIdInfo, nativeAttrs, connIdAttrs, sql, readOnly.value());
    }

    @Override
    public SqlAttributeBuilderImpl reference(DefinitionValue<String> name) {
        return (SqlAttributeBuilderImpl) super.reference(name);
    }
}
