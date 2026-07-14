package com.evolveum.polygon.sql.base.build.api;


import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.concepts.SourceLocation;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.sql.base.objectclass.SqlObjectClassMapping;
import com.evolveum.polygon.sql.base.schema.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.sync.SyncOperationDefinition;
import com.evolveum.polygon.sql.base.sync.SyncStrategy;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SqlObjectClassSchemaBuilderImpl extends BaseObjectClassDefinitionBuilder<
        SqlObjectClassSchemaBuilder,
        SqlObjectClassDefinition,
        SqlAttributeBuilder<SqlAttributeBuilder.Reference>,
        SqlAttributeBuilder.Reference,
        SqlAttributeBuilderImpl, SqlAttributeDefinition> implements SqlObjectClassSchemaBuilder {

    private DefinitionValue<String> schema = DefinitionValue.emptyDefault();
    private DefinitionValue<String> table;
private Boolean onlyExplicitlyListed = false;
    private SyncBuilder syncBuilder;
    private final java.util.Set<String> explicitRemoteNames = new java.util.LinkedHashSet<>();

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
    public java.util.Set<String> getExplicitRemoteNames() {
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
    public SyncOperationSchemaBuilder sync() {
        if (syncBuilder == null) {
            syncBuilder = new SyncBuilder();
        }
        return syncBuilder;
    }

    /**
     * Returns the sync builder, or null if sync is not configured.
     */
    public SyncBuilder getSyncBuilder() {
        return syncBuilder;
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

        SyncOperationDefinition syncDef = null;
        if (syncBuilder != null && syncBuilder.isConfigured()) {
            syncDef = syncBuilder.build();
        } else {
            // Auto-enable sync if the table has an "updated_at" column
            if (hasExplicitRemoteName("updated_at")) {
                SyncBuilder autoSync = new SyncBuilder();
                autoSync.timestampColumn("updated_at");
                if (hasExplicitRemoteName("deleted_at")) {
                    autoSync.deletedAtColumn("deleted_at");
                }
                syncDef = autoSync.build();
            }
        }

        var def = new SqlObjectClassDefinition(connIdInfo, nativeAttrs, connIdAttrs);
        def.setSync(syncDef);
        return def;
    }

    /**
     * Internal builder for sync configuration, implementing {@link SyncOperationSchemaBuilder}.
     */
    public static class SyncBuilder implements SyncOperationSchemaBuilder {
        private boolean enabled = true;
        private SyncStrategy strategy = SyncStrategy.TIMESTAMP_POLLING;
        private String timestampColumn;
        private String deletedAtColumn;
        private String auditTable;
        private boolean databaseToken;

        @Override
        public void enabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean getEnabled() {
            return enabled;
        }

        @Override
        public void strategy(SyncStrategy strategy) {
            this.strategy = strategy;
        }

        @Override
        public void strategy(String strategyName) {
            if (strategyName == null) return;
            try {
                this.strategy = SyncStrategy.valueOf(strategyName.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unknown sync strategy: " + strategyName
                        + ". Available: TIMESTAMP_POLLING, AUDIT_TABLE, POSTGRESQL_XMIN, ORACLE_ROWVERSION, SQLITE_ROWID", e);
            }
        }

        @Override
        public SyncStrategy getStrategy() {
            return strategy;
        }

        @Override
        public void timestampColumn(String timestampColumn) {
            this.timestampColumn = timestampColumn;
        }

        @Override
        public String getTimestampColumn() {
            return timestampColumn;
        }

        @Override
        public void deletedAtColumn(String deletedAtColumn) {
            this.deletedAtColumn = deletedAtColumn;
        }

        @Override
        public String getDeletedAtColumn() {
            return deletedAtColumn;
        }

        @Override
        public void auditTable(String auditTable) {
            this.auditTable = auditTable;
        }

        @Override
        public String getAuditTable() {
            return auditTable;
        }

        @Override
        public void databaseToken(boolean databaseToken) {
            this.databaseToken = databaseToken;
        }

        @Override
        public boolean getDatabaseToken() {
            return databaseToken;
        }

        /**
         * Returns true if sync is explicitly configured (not just default).
         */
        public boolean isConfigured() {
            // Consider it configured if strategy is not the default (TIMESTAMP_POLLING), or if any non-default option is set
            if (strategy != SyncStrategy.TIMESTAMP_POLLING || databaseToken) {
                return true;
            }
            if (timestampColumn != null || deletedAtColumn != null || auditTable != null) {
                return true;
            }
            if (!enabled) {
                return true;
            }
            return false;
        }

        /**
         * Builds the {@link SyncOperationDefinition}.
         */
        public SyncOperationDefinition build() {
            String tsCol = timestampColumn;
            if (strategy == SyncStrategy.TIMESTAMP_POLLING && (tsCol == null || tsCol.isBlank())) {
                tsCol = "updated_at";
            }
            if (strategy == SyncStrategy.POSTGRESQL_XMIN) {
                tsCol = "xmin";
            }
            if (strategy == SyncStrategy.SQLITE_ROWID) {
                tsCol = "rowid";
            }
            return new SyncOperationDefinition(
                    enabled, strategy,
                    tsCol, deletedAtColumn, auditTable, databaseToken);
        }
    }
}
