package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.schema.BaseSchema;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassSchemaBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlSchemaBuilderImpl;
import com.evolveum.polygon.sql.base.schema.strategy.DefaultDetectionStrategy;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.spi.Connector;

import java.util.*;

import static com.evolveum.polygon.conndev.concepts.DefinitionValue.detected;

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
        var builder = new SqlSchemaBuilderImpl(connectorClass, contextLookup);
        for (SqlTableInfo table : tables) {
            translateTable(table, builder);
        }
        // FIXME: These should be handled differently
        /*
        for (ObjectClassInfo info : additionalObjectClasses) {
            builder.defineObjectClass(info);
        }*/
        return builder.build();
    }

    private void translateTable(SqlTableInfo table, SqlSchemaBuilderImpl builder) {
        if (table == null || table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }

        List<AttributeDetectionStrategy> effectiveStrategies = getEffectiveStrategies();


        var maybeClassName = detected(table.getName());
        var objectClass = builder.correlateObjectClass(
                o -> Objects.equals(o.sql().schema(), table.getSchema())  && o.sql().table().equals(table.getName()),
                maybeClassName,
                o -> o.sql().schema(detected(table.getSchema())).table(detected(table.getName())));
        // Cast to impl type to set locator and namespace (SqlObjectClassSchemaBuilderImpl -> BaseObjectClassDefinitionBuilder)
        @SuppressWarnings("rawtypes")
        var implClass = (com.evolveum.polygon.conndev.schema.BaseObjectClassDefinitionBuilder) objectClass;
        implClass.locator(table.getName());
        if (table.getSchema() != null) {
            implClass.namespace(table.getSchema());
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

    private void translateColumn(SqlColumnMeta column, SqlObjectClassSchemaBuilder objectClass) {
        var attribute = objectClass.attribute(column.getName()).self();
        var connId = attribute.connId();
        connId.required(detected(!column.isNullable()));
        if (isLargeType(column.getTypeName())) {
            connId.returnedByDefault(detected(false));
        }
        // DB-generated identity columns cannot be written; primary keys are not updatable.
        if (column.isAutoIncrement()) {
            connId.creatable(detected(false));
            connId.updatable(detected((false)));
        } else if (column.isPrimaryKey()) {
            connId.updatable(detected(false));
        }
        // A foreign key is a reference. Columns of one (composite) FK share the same reference name
        // (subtype), and each carries its own target column.
        if (column.getReferencedTable() != null) {
            attribute.objectClass(column.getReferencedTable().toLowerCase());
            attribute.role(AttributeInfo.RoleInReference.SUBJECT);
            if (column.getForeignKeyName() != null) {
                attribute.subtype(column.getForeignKeyName());
            }
            /*
            if (column.getReferencedColumn() != null) {

                attribute.referencedAttribute(column.getReferencedColumn());
            }
             */

        } else {
            attribute.connId().type(detected(connIdType(column.getJavaType(), column.getTypeName())));
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

    private Class<?> connIdType(java.lang.reflect.Type javaType, String typeName) {
        // Use QueryDSL resolved Java type when available and concrete
        if (javaType != null && javaType instanceof Class<?> clazz) {
            Class<?> resolved = mapJavaTypeToConnId(clazz);
            if (resolved != null) {
                return resolved;
            }
        }
        // Fallback to driver-based type name string matching
        return connIdType(typeName);
    }

    /**
     * Maps a Java Class (from QueryDSL Configuration) to the ConnId wire type.
     * Returns null when the class is not a known mapped type, so the translator
     * falls back to the raw SQL TYPE_NAME.
     */
    private Class<?> mapJavaTypeToConnId(Class<?> javaType) {
        // Strings and dates → String
        if (javaType == String.class) {
            return String.class;
        }
        if (java.math.BigInteger.class.isAssignableFrom(javaType)) {
            return Long.class;
        }
        if (java.math.BigDecimal.class.isAssignableFrom(javaType)) {
            return Double.class;
        }
        if (java.sql.Date.class.isAssignableFrom(javaType)) {
            return String.class;
        }
        if (java.sql.Time.class.isAssignableFrom(javaType)) {
            return String.class;
        }
        if (java.sql.Timestamp.class.isAssignableFrom(javaType)) {
            return String.class;
        }

        // Integers (including Byte/Short) → Long
        if (Number.class.isAssignableFrom(javaType)) {
            return Long.class;
        }

        // Primitives (boolean, double, float)
        if (boolean.class.equals(javaType) || Boolean.class.equals(javaType)) {
            return Boolean.class;
        }
        if (double.class.equals(javaType) || double.class.equals(javaType) || Double.class.equals(javaType) ||
            Float.class.equals(javaType) || float.class.equals(javaType)) {
            return Double.class;
        }
        if (byte[].class.isAssignableFrom(javaType)) {
            return byte[].class;
        }

        return null; // No known mapping — fall through to typeName lookup
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
