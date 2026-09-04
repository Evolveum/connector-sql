/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.sql.base.build.api.*;
import com.evolveum.polygon.sql.base.groovy.impl.SqlObjectOperationBuilderImpl;
import com.evolveum.polygon.sql.base.schema.ChildTableRelationship.*;
import com.evolveum.polygon.sql.base.schema.strategy.*;
import com.evolveum.polygon.sql.base.schema.strategy.ChildTableRelationshipDetectionRule;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.spi.Connector;

import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

/**
 * Translates JDBC-detected tables into the conndev {@link BaseSchema} using a unified
 * strategy-based approach. Detection strategies examine table and column metadata and produce a
 * {@code MappingAction} — see {@link SqlResourceMappingRule}/{@link SqlAttributeMappingRule}. A
 * rule whose action also affects operation handlers is re-evaluated later by
 * {@link #applyHandlerRulesFor}.
 *
 * <p>{@link #translate} only populates the builder and returns it unbuilt. The caller must call
 * {@link #applyRules()} and then {@code build()} itself, in that order.
 *
 * @see SqlResourceMappingRule
 * @see SqlAttributeMappingRule
 * @see SqlSchemaBuilderImpl
 */
public class SqlSchemaTranslator {

    private final SqlSchemaBuilderImpl builder;
    private final List<SqlTableInfo> tables;
    private final List<SqlResourceMappingRule> resourceRules = new ArrayList<>();
    private final List<SqlAttributeMappingRule> attributeRules = new ArrayList<>();

    private final Map<String, List<ChildTableRelationship>> relationshipMap = new LinkedHashMap<>();
    private final Set<String> embeddedChildTableNames = new LinkedHashSet<>();
    private final Set<String> simpleAttributeChildTableNames = new LinkedHashSet<>();
    private final Set<String> junctionTableNames = new LinkedHashSet<>();

    /** Object classes this translator correlated to a table (see {@link #translateTable}), kept
     * here rather than on the builder itself, since the correlation is this translator's concern. */
    private final Map<SqlObjectClassSchemaBuilderImpl, SqlTableInfo> correlatedTables = new LinkedHashMap<>();

    private Class<? extends Connector> connectorClass;
    private ContextLookup contextLookup;

    public SqlSchemaTranslator(List<SqlTableInfo> tables) {
        this(null, tables);
    }

    public SqlSchemaTranslator(SqlSchemaBuilderImpl builder, List<SqlTableInfo> tables) {
        this.builder = builder == null
                ? new SqlSchemaBuilderImpl(placeholderConnectorClass(), null)
                : builder;
        this.tables = tables == null || tables.isEmpty() ? Collections.emptyList() : new ArrayList<>(tables);
        registerDefaultStrategies();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Connector> placeholderConnectorClass() {
        return (Class<? extends Connector>) (Class<?>) Object.class;
    }

    private void registerDefaultStrategies() {
        // Column-level rules
        attributeRules.add(new NullableAttributesAreNotRequiredRule());
        attributeRules.add(new LargeTypesNotReturnedByDefaultRule());
        attributeRules.add(new PrimaryKeyIsNotUpdatableRule());
        attributeRules.add(new AutoIncrementColumnIsNotEditableRule());
        // Explicit columns filter (applied at end when builder flag is set)
        attributeRules.add(new ExplicitColumnsMappingRule(() -> this.builder));
        // Table-level rules
        resourceRules.add(new ViewsShouldBeReadOnlyRule());
        resourceRules.add(new SinglePrimaryKeyIsUidRule());
        resourceRules.add(new CompositePkUidMappingRule());
        resourceRules.add(new UniqueAttributeAsFallbackUidRule());
        // Child table relationship detection (requires pre-phase)
        resourceRules.add(new ChildTableRelationshipDetectionRule(this));
    }

    public SqlSchemaTranslator connector(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        this.connectorClass = connectorClass;
        this.contextLookup = contextLookup;
        return this;
    }

    public SqlSchemaTranslator addResourceRule(SqlResourceMappingRule rule) {
        this.resourceRules.add(rule);
        return this;
    }

    public SqlSchemaTranslator addAttributeRule(SqlAttributeMappingRule rule) {
        this.attributeRules.add(rule);
        return this;
    }

    /**
     * Populates the underlying schema builder from the discovered tables — does not freeze it.
     * Callers must call {@link #applyRules()} and then {@code build()} themselves.
     *
     * @return the populated, not-yet-built schema builder
     */
    public SqlSchemaBuilderImpl translate(Collection<ObjectClassInfo> additionalObjectClasses) {
        additionalObjectClasses.forEach(builder::defineObjectClass);
        return translateInternal();
    }

    public List<ChildTableRelationship> getTableRelationships(String tableName) {
        var rels = relationshipMap.get(tableName);
        return rels != null ? rels : Collections.emptyList();
    }

    public Set<String> getEmbeddedChildTables() {
        return Collections.unmodifiableSet(embeddedChildTableNames);
    }

    public Set<String> getJunctionTables() {
        return Collections.unmodifiableSet(junctionTableNames);
    }

    private record ForeignKeyMeta(String childTable, String childColumn,
                                  String targetTable, String referencedColumn,
                                  boolean isConstraintBased) {}

    @Deprecated
    public SqlSchemaBuilderImpl translate(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        this.connectorClass = connectorClass;
        this.contextLookup = contextLookup;
        return translateInternal();
    }

    @Deprecated
    public SqlSchemaTranslator addStrategy(AttributeDetectionStrategy strategy) {
        return this;
    }

    private SqlSchemaBuilderImpl translateInternal() {
        detectRelationships();
        for (SqlTableInfo table : tables) {
            translateTable(table);
        }
        return builder;
    }

    /**
     * Pre-phase: analyzes all tables for foreign key relationships and classifies
     * child tables as SINGLE_VALUE_EMBEDDED, MULTI_VALUE_EMBEDDED, or JUNCTION_TABLE.
     */
    private void detectRelationships() {
        // Build case-insensitive table name lookup
        Map<String, SqlTableInfo> tableLookup = new HashMap<>();
        for (SqlTableInfo table : tables) {
            tableLookup.put(table.getName().toUpperCase(), table);
        }

        // Collect all FK constraints and convention-based FKs
        List<ForeignKeyMeta> allFks = new ArrayList<>();

        for (SqlTableInfo childTable : tables) {
            var childName = childTable.getName();
            Set<String> fkTargetTables = new LinkedHashSet<>();
            Map<String, ForeignKeyMeta> fkColumns = new LinkedHashMap<>();

            for (SqlColumnMeta col : childTable.getColumns()) {
                // Constraint-based FK
                if (col.getReferencedTable() != null && col.getForeignKeyName() != null) {
                    var refTable = col.getReferencedTable();
                    fkTargetTables.add(refTable.toUpperCase());
                    fkColumns.putIfAbsent(col.getName().toUpperCase(),
                            new ForeignKeyMeta(childName, col.getName(), refTable, col.getReferencedColumn(), true));
                }
                // Convention-based FK: column ends with _id and a matching table exists
                if (col.getName().toLowerCase().endsWith("_id") && col.getReferencedTable() == null) {
                    var possibleTable = col.getName().substring(0, col.getName().length() - 3).toLowerCase();
                    if (tableLookup.containsKey(possibleTable.toUpperCase())) {
                        fkTargetTables.add(possibleTable.toUpperCase());
                        if (!fkColumns.containsKey(col.getName().toUpperCase())) {
                            fkColumns.put(col.getName().toUpperCase(),
                                    new ForeignKeyMeta(childName, col.getName(),
                                            possibleTable, "id", false));
                        }
                    }
                }
            }

            // Only process if this table has FK references
            if (!fkTargetTables.isEmpty()) {
                allFks.addAll(fkColumns.values());

                // Classify based on number of FK targets
                if (fkTargetTables.size() >= 2) {
                    // FKs to 2+ tables: check if PK columns = FK columns (true junction pattern)
                    Set<String> pkCols = childTable.getColumns().stream()
                            .filter(SqlColumnMeta::isPrimaryKey)
                            .map(c -> c.getName().toUpperCase())
                            .collect(HashSet::new, (s, c) -> s.add(c), Set::addAll);
                    Set<String> fkCols = fkColumns.keySet();
                    boolean fkColsArePk = fkCols.equals(pkCols);

                    if (fkColsArePk) {
                        // JUNCTION: FKs to 2+ tables AND FK columns are the PK
                        // Tables with independent PK (like project_membership with separate 'id')
                        // are NOT treated as junction tables
                        junctionTableNames.add(childName.toUpperCase());

                        // Find the target table info for each FK target
                        for (String targetKey : fkTargetTables) {
                            var junctionTableForTarget = tableLookup.get(targetKey);
                            if (junctionTableForTarget == null) {
                                continue;
                            }

                            // Collect the FKs between junction and other targets (parent side)
                            for (String otherTarget : fkTargetTables) {
                                if (otherTarget.equals(targetKey)) {
                                    continue;
                                }
                                var otherTable = tableLookup.get(otherTarget);
                                if (otherTable == null) {
                                    continue;
                                }

                                // Parent → target reference through junction
                                List<JoinKey> parentKeys = new ArrayList<>();
                                for (var fk : fkColumns.values()) {
                                    if (fk.targetTable().toUpperCase().equals(otherTarget)) {
                                        var referencedColumn = fk.referencedColumn() != null
                                                ? fk.referencedColumn() : "id";
                                        parentKeys.add(new JoinKey(referencedColumn, fk.childColumn()));
                                    }
                                }

                                List<JoinKey> targetKeys = new ArrayList<>();
                                for (var fk : fkColumns.values()) {
                                    if (fk.targetTable().toUpperCase().equals(targetKey)) {
                                        var referencedColumn = fk.referencedColumn() != null
                                                ? fk.referencedColumn() : "id";
                                        targetKeys.add(new JoinKey(referencedColumn, fk.childColumn()));
                                    }
                                }

                                if (!parentKeys.isEmpty() && !targetKeys.isEmpty()) {
                                    var parentName = otherTable.getName();
                                    relationshipMap.computeIfAbsent(parentName, k -> new ArrayList<>())
                                            .add(new JunctionRelationship(
                                                    parentName, childName,
                                                    parentKeys, targetKeys,
                                                    junctionTableForTarget.getName(),
                                                    ChildTableType.JUNCTION_TABLE, false));
                                }
                            }
                        }
                    }
                    // Tables with 2+ FK targets but FK ≠ PK: no special treatment,
                    // handled like regular tables with multiple foreign key columns
                } else {
                    // Embedded relationship: FK to exactly 1 table
                    // Detect when FK columns are part of the PK:
                    //   - SINGLE_VALUE: PK == FK columns only (one-to-one)
                    //   - MULTI_VALUE_ATTRIBUTE: PK ⊃ FK  + all non-FK PK columns are value columns
                    //     → becomes a normal multivalue attribute (not EmbeddedObject)
                    //   - MULTI_VALUE_EMBEDDED: everything else (surrogate PK, etc.)
                    // Tables whose FK is NOT part of PK remain standalone
                    Set<String> pkCols = new HashSet<>();
                    Set<String> fkCols = new HashSet<>();
                    for (SqlColumnMeta col : childTable.getColumns()) {
                        if (col.isPrimaryKey()) {
                            pkCols.add(col.getName().toUpperCase());
                        }
                        if (fkColumns.containsKey(col.getName().toUpperCase())) {
                            fkCols.add(col.getName().toUpperCase());
                        }
                    }

                    // Detect as child table if FK columns are part of the PK
                    // (FK forms the "identity" linking to parent)
                    boolean fkIsPartOfPk = !fkCols.isEmpty() && !pkCols.isEmpty() && pkCols.containsAll(fkCols);

                    if (fkIsPartOfPk) {
                        var targetKey = fkTargetTables.iterator().next();
                        var targetTable = tableLookup.get(targetKey);
                        if (targetTable == null) {
                            continue;
                        }

                        // PK == FK only → one-to-one (single-valued)
                        boolean pkEqualsFkOnly = pkCols.equals(fkCols);

                        // Count ALL non-FK columns in the table (not just PK)
                        // FK + single value column → multi-value simple attribute
                        // FK + additional data columns → multi-value embedded objects
                        Set<String> nonFkCols = childTable.getColumns().stream()
                                .filter(col -> !fkColumns.containsKey(col.getName().toUpperCase()))
                                .map(col -> col.getName().toUpperCase())
                                .collect(Collectors.toCollection(LinkedHashSet::new));

                        // Build join keys from FK columns
                        List<JoinKey> joinKeys = new ArrayList<>();
                        boolean anyConvention = false;
                        for (var fk : fkColumns.values()) {
                            String parentCol = fk.referencedColumn() != null ? fk.referencedColumn() : "id";
                            joinKeys.add(new JoinKey(parentCol, fk.childColumn()));
                            anyConvention = anyConvention || !fk.isConstraintBased();
                        }
                        var parentName = targetTable.getName();

                        if (pkEqualsFkOnly) {
                            embeddedChildTableNames.add(childName.toUpperCase());
                            relationshipMap.computeIfAbsent(parentName, k -> new ArrayList<>())
                                    .add(new EmbeddedRelationship(
                                            parentName, childName,
                                            joinKeys, ChildTableType.SINGLE_VALUE_EMBEDDED, anyConvention));
                        } else if (nonFkCols.size() == 1) {
                            // FK + exactly one additional column → simple multivalue attribute
                            // Child table is NOT an embedded OC, just a scalar attribute on parent
                            simpleAttributeChildTableNames.add(childName.toUpperCase());
                            SqlColumnMeta valueCol = null;
                            for (SqlColumnMeta col : childTable.getColumns()) {
                                if (!fkColumns.containsKey(col.getName().toUpperCase())) {
                                    valueCol = col;
                                    break;
                                }
                            }
                            if (valueCol != null) {
                                relationshipMap.computeIfAbsent(parentName, k -> new ArrayList<>())
                                        .add(new SimpleAttributeRelationship(
                                                parentName, childName,
                                                joinKeys, valueCol,
                                                ChildTableType.MULTI_VALUE_ATTRIBUTE, anyConvention));
                            }
                        } else {
                            // FK + multiple additional columns → multi-value embedded objects
                            embeddedChildTableNames.add(childName.toUpperCase());
                            relationshipMap.computeIfAbsent(parentName, k -> new ArrayList<>())
                                    .add(new EmbeddedRelationship(
                                            parentName, childName,
                                            joinKeys, ChildTableType.MULTI_VALUE_EMBEDDED, anyConvention));
                        }
                    }
                }
            }
        }
    }
    private void translateTable(SqlTableInfo table) {
        if (table == null || table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }
        // Junction tables and simple-attribute child tables are invisible — no OC created for them
        if (junctionTableNames.contains(table.getName().toUpperCase())) {
            return;
        }
        if (simpleAttributeChildTableNames.contains(table.getName().toUpperCase())) {
            return;
        }
        if (builder.getOnlyExplicitlyListed() != null && builder.getOnlyExplicitlyListed()
                && !hasCorrelatedBuilder(table)) {
            return;
        }

        // Phase 1: Correlate builder
        var objectClass = correlateBuilder(table);

        // Phase 2: Create attributes with core setup (identity only — rule evaluation and
        // application is deferred to #applyRules).
        for (SqlColumnMeta column : getIncludedColumns(table)) {
            var attribute = (SqlAttributeBuilderImpl) objectClass.attribute(column.getName());
            setupCoreAttribute(attribute, column);
        }

        // Defer rule dispatch to #applyRules — this table's metadata must still be reachable
        // then, since it may run long after this table is processed.
        correlatedTables.put(objectClass, table);
    }

    /**
     * Applies {@link #applyRulesFor} to every object class this translator correlated to a table
     * (see {@link #translateTable}). Must be called before {@code build()}.
     */
    public void applyRules() {
        for (var entry : correlatedTables.entrySet()) {
            applyRulesFor(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Evaluates and applies this translator's rules against the given table. Handler effects are
     * not applied here — see {@link #applyHandlerRulesFor}.
     */
    public void applyRulesFor(SqlTableInfo table, SqlObjectClassSchemaBuilderImpl objectClass) {
        // Column-level strategies
        for (SqlColumnMeta column : getIncludedColumns(table)) {
            var attribute = (SqlAttributeBuilderImpl) objectClass.attribute(column.getName());
            var context = new SqlAttributeMappingRule.Context(table, column);
            for (SqlAttributeMappingRule rule : attributeRules) {
                if (rule.checkIfApplicable(context, objectClass, attribute)) {
                    var action = rule.createAction(context);
                    if (action != null) {
                        action.applyToAttribute(attribute);
                    }
                }
            }
        }

        // Table-level rules (UID renaming/composite PK handled by UID strategies)
        for (SqlResourceMappingRule rule : resourceRules) {
            if (rule.checkIfApplicable(table, objectClass, null)) {
                var action = rule.createAction(table);
                if (action != null) {
                    action.applyToSchema(objectClass);
                }
            }
        }
    }

    /**
     * Re-evaluates resource-level rules against the given object class's correlated table,
     * applying any handler effect to the given handler builder. Called once handlers exist.
     */
    public void applyHandlerRulesFor(SqlObjectClassSchemaBuilderImpl objectClass, SqlObjectOperationBuilderImpl handlerBuilder) {
        var table = correlatedTables.get(objectClass);
        if (table == null) {
            return;
        }
        for (SqlResourceMappingRule rule : resourceRules) {
            if (rule.checkIfApplicable(table, objectClass, null)) {
                var action = rule.createAction(table);
                if (action != null) {
                    action.applyToHandler(handlerBuilder);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private SqlObjectClassSchemaBuilderImpl correlateBuilder(SqlTableInfo table) {
        var maybeClassName = detected(table.getName());
        var objectClass = (SqlObjectClassSchemaBuilderImpl) builder.correlateObjectClass(
                o -> {
                    var sqlSchema = o.sql().schema();
                    var sqlTable = o.sql().table();
                    if (sqlSchema == null) sqlSchema = "";
                    if (sqlTable == null) sqlTable = "";
                    return (sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema()))
                            && sqlTable.equals(table.getName());
                },
                maybeClassName,
                o -> o.sql().schema(detected(table.getSchema())).table(detected(table.getName()))
        );
        // Enrich existing explicit definitions as well as newly created ones. Declared values
        // retain precedence, while omitted schema/table values receive their JDBC metadata value.
        objectClass.sql()
                .schema(detected(table.getSchema()))
                .table(detected(table.getName()));
        return objectClass;
    }

    private void setupCoreAttribute(SqlAttributeBuilder.Reference attribute, SqlColumnMeta column) {
        var sql = attribute.sql();
        sql.column(detected(column.getName()));
        var mapping = column.getValueMapping();
        if (mapping != null) {
            attribute.connId().type(detected(mapping.connIdType()));
            sql.valueMapping(DefinitionValue.detected(mapping));
        } else {
            attribute.connId().type(String.class);
        }
        if (column.getReferencedTable() != null && column.getForeignKeyName() != null) {
            attribute.subtype(column.getForeignKeyName());
        }
    }

    private List<SqlColumnMeta> getIncludedColumns(SqlTableInfo table) {
        List<SqlColumnMeta> columns = new ArrayList<>(table.getColumns());
        if (Boolean.TRUE.equals(builder.getOnlyExplicitlyListed())) {
            columns = filterExplicitColumns(table);
        }
        return columns;
    }

    private List<SqlColumnMeta> filterExplicitColumns(SqlTableInfo table) {
        if (!hasExplicitAttribute(table)) {
            return table.getColumns();
        }
        return table.getColumns().stream()
                .filter(col -> isExplicitColumn(table, col.getName()))
                .toList();
    }

    private boolean hasExplicitAttribute(SqlTableInfo table) {
        for (SqlObjectClassSchemaBuilderImpl oc : builder.allObjectClassBuilders()) {
            if (sqlMatches(oc, table)) {
                return !oc.getExplicitRemoteNames().isEmpty();
            }
        }
        return false;
    }

    private boolean isExplicitColumn(SqlTableInfo table, String columnName) {
        for (SqlObjectClassSchemaBuilderImpl oc : builder.allObjectClassBuilders()) {
            if (sqlMatches(oc, table)) {
                return oc.hasExplicitRemoteName(columnName);
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasCorrelatedBuilder(SqlTableInfo table) {
        List<SqlObjectClassSchemaBuilder> builders =
                (List<SqlObjectClassSchemaBuilder>) (List<?>) builder.allObjectClassBuilders();
        for (SqlObjectClassSchemaBuilder oc : builders) {
            if (sqlMatches(oc, table)) {
                return true;
            }
        }
        return false;
    }

    private boolean sqlMatches(SqlObjectClassSchemaBuilder oc, SqlTableInfo table) {
        var sqlSchema = oc.sql().schema();
        var sqlTable = oc.sql().table();
        return (sqlSchema.isEmpty() || sqlSchema.equals(table.getSchema()))
                && sqlTable.equals(table.getName());
    }
}
