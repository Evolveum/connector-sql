package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.api.build.*;
import com.evolveum.polygon.sql.base.schema.strategy.DefaultDetectionStrategy;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.Schema;

import java.util.*;

/**
 * Translates a JDBC-detected SQL schema into ConnId schema-based builder objects.
 * <p>
 * Uses composition of {@link AttributeDetectionStrategy}s to determine:
 * <ul>
 *   <li>Which columns become ConnId attributes</li>
 *   <li>Which column is the ConnId UID (default: single non-composite PK)</li>
 *   <li>Which tables are embedded</li>
 * </ul>
 */
public class SqlSchemaTranslator {

    private final SqlSchema sourceSchema;
    private SqlSchemaBuilder builder;
    private final List<AttributeDetectionStrategy> strategies = new ArrayList<>();

    public SqlSchemaTranslator(SqlSchema sourceSchema) {
        this.sourceSchema = sourceSchema;
    }

    public SqlSchemaTranslator addStrategy(AttributeDetectionStrategy strategy) {
        this.strategies.add(strategy);
        return this;
    }

    public boolean translate() {
        if (sourceSchema == null || sourceSchema.getTables() == null || sourceSchema.getTables().isEmpty()) {
            return false;
        }

        builder = new DefaultSqlSchemaBuilder();

        for (SqlTableInfo table : sourceSchema.getTables()) {
            translateTable(table);
        }
        return true;
    }

    public SqlSchemaBuilder getBuilder() {
        return builder;
    }

    public Schema toConnIdSchema() {
        Set<ObjectClassInfo> objectClassInfos = new HashSet<>();

        if (sourceSchema == null || sourceSchema.getTables() == null || sourceSchema.getTables().isEmpty()) {
            return new Schema(
                    Collections.unmodifiableSet(objectClassInfos),
                    Collections.emptySet(),
                    Map.of(), Map.of());
        }

        for (SqlTableInfo table : sourceSchema.getTables()) {
            ObjectClassInfo objClassInfo = translateTableToObjClassInfo(table);
            objectClassInfos.add(objClassInfo);
        }

        return new Schema(
                Collections.unmodifiableSet(objectClassInfos),
                Collections.emptySet(),
                Map.of(), Map.of());
    }

    private void translateTable(SqlTableInfo table) {
        if (table == null || table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }

        String objectClassName = table.getName().toLowerCase();
        SqlObjectClassSchemaBuilder objClassBuilder = builder.objectClass(objectClassName);

        if (objClassBuilder instanceof DefaultSqlObjectClassSchemaBuilder defaultObj) {
            defaultObj.setObjectClassName(objectClassName);
        }

        List<AttributeDetectionStrategy> effectiveStrategies = getEffectiveStrategies();

        for (AttributeDetectionStrategy strategy : effectiveStrategies) {
            if (strategy.isEmbedded(table)) {
                if (objClassBuilder instanceof DefaultSqlObjectClassSchemaBuilder schemaBuilder) {
                    schemaBuilder.setEmbedded(true);
                }
                break;
            }
        }

        Optional<SqlColumnMeta> uidColumn = findUid(table, effectiveStrategies);

        List<SqlColumnMeta> attributeColumns = findAttributeColumns(table, effectiveStrategies);

        for (SqlColumnMeta column : attributeColumns) {
            translateColumn(column, objClassBuilder, uidColumn);
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

    private void translateColumn(SqlColumnMeta column, SqlObjectClassSchemaBuilder objClassBuilder,
                                  Optional<SqlColumnMeta> uidColumn) {
        String attrName = column.getName();

        SqlAttributeBuilder attrBuilder = objClassBuilder.attribute(attrName);

        if (attrBuilder instanceof DefaultSqlAttributeBuilder dbAttr) {
            dbAttr.required(!column.isNullable())
                    .returnedByDefault(!isLargeType(column.getTypeName()));
            if (column.isAutoIncrement()) {
                dbAttr.setAutoIncrement(Boolean.TRUE);
            }
        }

        if (uidColumn.isPresent() && uidColumn.get().equals(column)) {
            if (attrBuilder instanceof DefaultSqlAttributeBuilder attributeBuilder) {
                attributeBuilder.setConnIdName("UID");
            }
        }
    }

    private ObjectClassInfo translateTableToObjClassInfo(SqlTableInfo table) {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        String objectClassName = table.getName().toLowerCase();
        builder.setType(objectClassName);

        List<AttributeDetectionStrategy> effectiveStrategies = getEffectiveStrategies();

        boolean isEmbedded = false;
        for (AttributeDetectionStrategy strategy : effectiveStrategies) {
            if (strategy.isEmbedded(table)) {
                isEmbedded = true;
                break;
            }
        }
        builder.setEmbedded(isEmbedded);

        String uidColumnName = null;
        for (AttributeDetectionStrategy strategy : effectiveStrategies) {
            Optional<SqlColumnMeta> uidCandidate = strategy.resolveUid(table);
            if (uidCandidate.isPresent()) {
                uidColumnName = uidCandidate.get().getName();

                break;
            }
        }

        for (SqlColumnMeta column : table.getColumns()) {
            AttributeInfoBuilder attrBuilder = new AttributeInfoBuilder();
            attrBuilder.setName(column.getName());
            attrBuilder.setType(connIdType(column.getTypeName()));
            attrBuilder.setRequired(!column.isNullable());
            attrBuilder.setReturnedByDefault(!isLargeType(column.getTypeName()));

            builder.addAttributeInfo(attrBuilder.build());
        }

        return builder.build();
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