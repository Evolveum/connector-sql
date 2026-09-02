/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTableAccess;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.SqlChildJoinConfig;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.EmbeddedObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Maps and writes one scalar or embedded attribute stored in an owned child table. */
final class SqlChildTableWriteHandler {

    private final SqlChildJoinConfig config;
    private final SqlTableAccess table;
    private final SqlObjectClassDefinition childDefinition;

    SqlChildTableWriteHandler(SqlBaseContext context, SqlChildJoinConfig config) {
        this.config = config;
        this.table = new SqlTableAccess(context, config.childTable(), "cw");
        this.childDefinition = context.schema() != null
                ? context.schema().objectClasses().stream()
                        .filter(candidate -> candidate.sql() != null
                                && candidate.sql().getTableName().equalsIgnoreCase(config.childTable()))
                        .findFirst()
                        .orElse(null)
                : null;
    }

    SqlChildJoinConfig config() {
        return config;
    }

    boolean supports(String attributeName) {
        return config.targetAttributeName().equalsIgnoreCase(attributeName);
    }

    void create(
            SqlConnection connection, Map<String, Object> parentValues,
            Collection<Attribute> attributes) {
        for (var attribute : attributes) {
            requireSupported(attribute.getName());
            insertValues(connection, parentValues, attribute.getValue(), false);
        }
    }

    void update(
            SqlConnection connection, Map<String, Object> parentValues,
            Collection<AttributeDelta> modifications) {
        for (var modification : modifications) {
            requireSupported(modification.getName());
            var replacements = modification.getValuesToReplace();
            if (replacements != null) {
                table.delete(connection, joinAssignments(parentValues));
                insertValues(connection, parentValues, replacements, false);
                continue;
            }
            deleteValues(connection, parentValues, modification.getValuesToRemove());
            insertValues(connection, parentValues, modification.getValuesToAdd(), true);
        }
    }

    void delete(SqlConnection connection, Map<String, Object> parentValues) {
        table.delete(connection, joinAssignments(parentValues));
    }

    private void insertValues(
            SqlConnection connection, Map<String, Object> parentValues,
            List<Object> values, boolean ignoreExisting) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (!config.multiValued() && values.size() > 1) {
            throw invalid("Attribute " + config.targetAttributeName()
                    + " accepts at most one value");
        }
        for (var value : values) {
            var assignments = config.valueColumn() != null
                    ? simpleAssignments(parentValues, value)
                    : embeddedAssignments(parentValues, value);
            if (!ignoreExisting || !table.exists(connection, assignments)) {
                table.insert(connection, assignments);
            }
        }
    }

    private void deleteValues(
            SqlConnection connection, Map<String, Object> parentValues, List<Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        for (var value : values) {
            var criteria = config.valueColumn() != null
                    ? simpleAssignments(parentValues, value)
                    : embeddedAssignments(parentValues, value);
            table.delete(connection, criteria);
        }
    }

    private Map<String, Object> simpleAssignments(
            Map<String, Object> parentValues, Object value) {
        var assignments = joinAssignments(parentValues);
        put(assignments, table.actualColumn(config.valueColumn()),
                table.toWireValue(config.valueColumn(), value));
        return assignments;
    }

    private Map<String, Object> embeddedAssignments(
            Map<String, Object> parentValues, Object value) {
        if (!(value instanceof EmbeddedObject embedded)) {
            throw invalid("Attribute " + config.targetAttributeName()
                    + " requires EmbeddedObject values");
        }

        var assignments = new LinkedHashMap<String, Object>();
        for (var attribute : embedded.getAttributes()) {
            var definition = childAttribute(attribute.getName());
            if (definition != null && definition.sql() != null) {
                var attributeValue = singleValue(attribute);
                for (var columnValue : definition.sql().columnValues(table.path(), attributeValue)) {
                    var column = table.actualColumn(columnValue.path().getMetadata().getName());
                    put(assignments, column, columnValue.value());
                }
                continue;
            }

            var column = table.column(attribute.getName());
            if (column == null) {
                throw invalid("Unknown child-table column " + attribute.getName()
                        + " for " + config.childTable());
            }
            put(assignments, column.getName(),
                    table.toWireValue(column.getName(), singleValue(attribute)));
        }

        for (var join : joinAssignments(parentValues).entrySet()) {
            if (assignments.containsKey(join.getKey())
                    && !Objects.deepEquals(assignments.get(join.getKey()), join.getValue())) {
                throw invalid("Embedded attribute " + config.targetAttributeName()
                        + " cannot change parent join column " + join.getKey());
            }
            assignments.put(join.getKey(), join.getValue());
        }
        return assignments;
    }

    private Map<String, Object> joinAssignments(Map<String, Object> parentValues) {
        var assignments = new LinkedHashMap<String, Object>();
        for (var key : config.joinKeys()) {
            if (!parentValues.containsKey(key.parentColumn())) {
                throw new IllegalArgumentException(
                        "Missing parent join value for column " + key.parentColumn());
            }
            put(assignments, table.actualColumn(key.childColumn()),
                    table.toWireValue(key.childColumn(), parentValues.get(key.parentColumn())));
        }
        return assignments;
    }

    private SqlAttributeDefinition childAttribute(String attributeName) {
        if (childDefinition == null) {
            return null;
        }
        var attribute = childDefinition.attributeFromConnIdName(attributeName);
        if (attribute != null) {
            return attribute;
        }
        return childDefinition.attributes().stream()
                .filter(candidate -> candidate.connId().getName().equalsIgnoreCase(attributeName)
                        || candidate.remoteName().equalsIgnoreCase(attributeName))
                .findFirst()
                .orElse(null);
    }

    private Object singleValue(Attribute attribute) {
        var values = attribute.getValue();
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw invalid("Child-table column " + attribute.getName()
                    + " accepts at most one value");
        }
        return values.getFirst();
    }

    private void put(Map<String, Object> assignments, String column, Object value) {
        if (assignments.containsKey(column)
                && !Objects.deepEquals(assignments.get(column), value)) {
            throw invalid("Conflicting values for child-table column " + column);
        }
        assignments.put(column, value);
    }

    private void requireSupported(String attributeName) {
        if (!supports(attributeName)) {
            throw invalid("Handler for " + config.targetAttributeName()
                    + " cannot process attribute " + attributeName);
        }
    }

    private InvalidAttributeValueException invalid(String message) {
        return new InvalidAttributeValueException(message);
    }
}
