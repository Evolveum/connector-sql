package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.conndev.schema.BaseSchemaBuilder;
import com.evolveum.polygon.sql.base.schema.strategy.DefaultDetectionStrategy;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.spi.Connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Translates the JDBC-detected tables into the conndev {@link BaseSchema} — the single source of the
 * default schema mapping. Both the ConnId schema for provisioning ({@link BaseSchema#connIdSchema()})
 * and the development-mode {@code conndev_ObjectClass} export are derived from the translated model;
 * nothing else maps the raw JDBC metadata.
 * <p>
 * Uses composition of {@link AttributeDetectionStrategy}s to determine:
 * <ul>
 *   <li>Which columns become ConnId attributes</li>
 *   <li>Which column is the ConnId UID (default: single non-composite PK)</li>
 *   <li>Which tables are embedded</li>
 * </ul>
 */
public class SqlSchemaTranslator {

    private final List<SqlTableInfo> tables;
    private final List<AttributeDetectionStrategy> strategies = new ArrayList<>();

    public SqlSchemaTranslator(List<SqlTableInfo> tables) {
        this.tables = tables == null ? List.of() : tables;
    }

    public SqlSchemaTranslator addStrategy(AttributeDetectionStrategy strategy) {
        this.strategies.add(strategy);
        return this;
    }

    public BaseSchema translate(Class<? extends Connector> connectorClass, ContextLookup contextLookup) {
        return translate(connectorClass, contextLookup, List.of());
    }

    /**
     * @param additionalObjectClasses ready-made ConnId object classes to expose alongside the tables
     *                                (e.g. the shared development-mode {@code conndev_*} classes)
     */
    public BaseSchema translate(Class<? extends Connector> connectorClass, ContextLookup contextLookup,
            Collection<ObjectClassInfo> additionalObjectClasses) {
        var builder = new BaseSchemaBuilder(connectorClass, contextLookup);
        for (SqlTableInfo table : tables) {
            translateTable(table, builder);
        }
        for (ObjectClassInfo info : additionalObjectClasses) {
            builder.defineObjectClass(info);
        }
        return builder.build();
    }

    private void translateTable(SqlTableInfo table, BaseSchemaBuilder builder) {
        if (table == null || table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }

        List<AttributeDetectionStrategy> effectiveStrategies = getEffectiveStrategies();

        BaseObjectClassDefinitionBuilder objectClass = builder.objectClass(table.getName().toLowerCase());
        objectClass.locator(table.getName());
        if (table.getSchema() != null) {
            objectClass.namespace(table.getSchema());
        }
        if (effectiveStrategies.stream().anyMatch(strategy -> strategy.isEmbedded(table))) {
            objectClass.embedded(true);
        }

        Optional<SqlColumnMeta> uidColumn = findUid(table, effectiveStrategies);
        List<SqlColumnMeta> attributeColumns = findAttributeColumns(table, effectiveStrategies);

        for (SqlColumnMeta column : attributeColumns) {
            translateColumn(column, objectClass);
        }
        if (uidColumn.isPresent() && attributeColumns.contains(uidColumn.get())) {
            objectClass.connIdAttribute("UID", uidColumn.get().getName());
        }
    }

    private void translateColumn(SqlColumnMeta column, BaseObjectClassDefinitionBuilder objectClass) {
        var attribute = objectClass.attribute(column.getName());
        attribute.nativeType(column.getTypeName());
        attribute.required(!column.isNullable());
        if (isLargeType(column.getTypeName())) {
            attribute.returnedByDefault(false);
        }
        // DB-generated identity columns cannot be written; primary keys are not updatable.
        if (column.isAutoIncrement()) {
            attribute.creatable(false);
            attribute.updatable(false);
        } else if (column.isPrimaryKey()) {
            attribute.updatable(false);
        }
        // A foreign key is a reference. Columns of one (composite) FK share the same reference name
        // (subtype), and each carries its own target column.
        if (column.getReferencedTable() != null) {
            attribute.objectClass(column.getReferencedTable().toLowerCase());
            attribute.role(AttributeInfo.RoleInReference.SUBJECT);
            if (column.getForeignKeyName() != null) {
                attribute.subtype(column.getForeignKeyName());
            }
            if (column.getReferencedColumn() != null) {
                attribute.referencedAttribute(column.getReferencedColumn());
            }
        } else {
            attribute.connId().type(connIdType(column.getTypeName()));
        }
    }

    private List<AttributeDetectionStrategy> getEffectiveStrategies() {
        List<AttributeDetectionStrategy> effectiveStrategies = new ArrayList<>();
        effectiveStrategies.add(new DefaultDetectionStrategy());
        effectiveStrategies.addAll(this.strategies);
        return effectiveStrategies;
    }

    private Optional<SqlColumnMeta> findUid(SqlTableInfo table,
                                             List<AttributeDetectionStrategy> strategies) {
        Optional<SqlColumnMeta> uid = Optional.empty();
        for (AttributeDetectionStrategy strategy : strategies) {
            uid = strategy.resolveUid(table);
            if (uid.isPresent()) {
                break;
            }
        }
        return uid;
    }

    private List<SqlColumnMeta> findAttributeColumns(SqlTableInfo table,
                                                      List<AttributeDetectionStrategy> strategies) {
        List<SqlColumnMeta> columns = new ArrayList<>(table.getColumns());
        for (AttributeDetectionStrategy strategy : strategies) {
            List<SqlColumnMeta> filtered = strategy.detectColumns(table);
            if (filtered != null && !filtered.isEmpty()) {
                columns = filtered;
            }
        }
        return columns;
    }

    private Class<?> connIdType(String typeName) {
        if (typeName == null) {
            return String.class;
        }
        String upper = typeName.toUpperCase();
        switch (upper) {
            case "VARCHAR":
            case "CHAR":
            case "TEXT":
            case "VARCHAR2":
            case "DATE":
            case "TIME":
            case "TIMESTAMP":
            case "DATETIME":
                return String.class;
            case "INTEGER":
            case "INT":
            case "SMALLINT":
            case "TINYINT":
            case "BIGINT":
                return Long.class;
            case "DECIMAL":
            case "NUMERIC":
            case "FLOAT":
            case "REAL":
            case "DOUBLE":
                return Double.class;
            case "BOOLEAN":
            case "BOOL":
                return Boolean.class;
            case "BLOB":
            case "BYTEA":
            case "VARBINARY":
                return byte[].class;
            default:
                return String.class;
        }
    }

    private boolean isLargeType(String typeName) {
        if (typeName == null) {
            return false;
        }
        String upper = typeName.toUpperCase();
        return upper.contains("BLOB") || upper.contains("CLOB")
                || upper.contains("BINARY") || upper.contains("VARBINARY");
    }
}
