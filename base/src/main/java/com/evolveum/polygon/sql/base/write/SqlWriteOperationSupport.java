/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.search.SqlSearchExecutor;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.dml.SQLInsertClause;
import com.querydsl.sql.dml.SQLUpdateClause;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared mapping, transaction, lookup, and exception support for SQL write operations.
 */
abstract class SqlWriteOperationSupport extends SqlSearchExecutor {

    protected SqlWriteOperationSupport(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        super(context, objectClass);
    }

    protected void requireWritable() {
        if (Boolean.TRUE.equals(objectClass.getReadOnly())) {
            throw new UnsupportedOperationException(
                    "Object class " + objectClass.name() + " is read-only");
        }
    }

    protected RelationalPathBase<?> tablePath() {
        return objectClass.sql().pathAlias("o");
    }

    protected SqlAttributeDefinition uidDefinition() {
        var definition = objectClass.attributeFromConnIdName(Uid.NAME);
        if (definition == null || definition.sql() == null) {
            throw new ConnectorException(
                    "Object class " + objectClass.name() + " does not define a SQL UID mapping");
        }
        return definition;
    }

    protected Uid suppliedUid(Collection<Attribute> attributes) {
        if (attributes == null) {
            return null;
        }
        for (var attribute : attributes) {
            if (Uid.NAME.equals(attribute.getName())) {
                var value = singleValue(attribute.getName(), attribute.getValue());
                if (value == null) {
                    throw invalid("UID must not be null");
                }
                return new Uid(value.toString());
            }
        }

        var uidDefinition = uidDefinition();
        var nameDefinition = objectClass.attributeFromConnIdName(Name.NAME);
        if (uidDefinition.connId().isCreateable()
                && nameDefinition != null
                && mapsSameColumns(nameDefinition, uidDefinition)) {
            for (var attribute : attributes) {
                if (Name.NAME.equals(attribute.getName())) {
                    var value = singleValue(attribute.getName(), attribute.getValue());
                    if (value == null) {
                        throw invalid("Name used as UID must not be null");
                    }
                    return new Uid(value.toString());
                }
            }
        }
        return null;
    }

    protected Map<Path<?>, Object> createAssignments(
            RelationalPathBase<?> table, Collection<Attribute> attributes) {
        var assignments = new LinkedHashMap<Path<?>, Object>();

        if (attributes != null) {
            for (var attribute : attributes) {
                var definition = requireAttribute(attribute.getName());
                var uidDefinition = uidDefinition();

                // The schema builder auto-creates __NAME__ from the UID mapping when no
                // separate name mapping exists. For generated keys it is only a ConnId
                // identifier placeholder and must not be inserted into the key column. For
                // natural/composite keys it carries the caller-supplied UID value.
                if (Name.NAME.equals(attribute.getName())
                        && mapsSameColumns(definition, uidDefinition)) {
                    if (uidDefinition.connId().isCreateable()) {
                        addAssignments(assignments, uidDefinition,
                                singleValue(attribute.getName(), attribute.getValue()), table);
                    }
                    continue;
                }

                if (definition.emulated()) {
                    // ConnId does not permit __UID__ in facade create requests. For a natural
                    // (non-generated) SQL key, the auto-emulated __NAME__ carries that key value.
                    if (Name.NAME.equals(attribute.getName())
                            && uidDefinition.connId().isCreateable()
                            && mapsSameColumns(definition, uidDefinition)) {
                        addAssignments(assignments, uidDefinition,
                                singleValue(attribute.getName(), attribute.getValue()), table);
                    }
                    continue;
                }
                if (!definition.connId().isCreateable()) {
                    throw invalid("Attribute " + attribute.getName() + " is not creatable");
                }
                addAssignments(assignments, definition,
                        singleValue(attribute.getName(), attribute.getValue()), table);
            }
        }

        return assignments;
    }

    protected Map<Path<?>, Object> updateAssignments(
            RelationalPathBase<?> table, ConnectorObject current,
            Collection<AttributeDelta> modifications) {
        var assignments = new LinkedHashMap<Path<?>, Object>();
        if (modifications == null) {
            return assignments;
        }

        for (var modification : modifications) {
            var definition = requireAttribute(modification.getName());
            if (definition.emulated() || !definition.connId().isUpdateable()) {
                throw invalid("Attribute " + modification.getName() + " is not updatable");
            }

            var before = current.getAttributeByName(modification.getName());
            if (before == null) {
                before = AttributeBuilder.build(modification.getName());
            }
            var after = modification.applyTo(before);
            addAssignments(assignments, definition,
                    singleValue(modification.getName(), after.getValue()), table);
        }
        return assignments;
    }

    protected BooleanExpression uidPredicate(RelationalPathBase<?> table, Uid uid) {
        if (uid == null || uid.getUidValue() == null) {
            throw invalid("UID must not be null");
        }
        var filter = uidDefinition().sql().sqlFilter();
        if (filter == null) {
            throw new ConnectorException(
                    "UID mapping for " + objectClass.name() + " does not support equality");
        }
        return filter.eq(table, uid.getUidValue());
    }

    protected ConnectorObject findByUid(
            SqlConnection connection, Uid uid, OperationOptions options, boolean includeAllAttributes) {
        var table = tablePath();
        Map<SqlAttributeDefinition, Collection<Path<?>>> selectedAttributes;
        if (includeAllAttributes) {
            selectedAttributes = new LinkedHashMap<>();
            for (var definition : objectClass.attributes()) {
                if (definition.sql() != null) {
                    selectedAttributes.put(definition, definition.sql().selectPaths(table));
                }
            }
        } else {
            selectedAttributes = selectColumns(table, options);
        }

        var columns = selectedAttributes.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toArray(Path<?>[]::new);
        if (columns.length == 0) {
            throw new ConnectorException("No SQL columns are mapped for " + objectClass.name());
        }

        var row = connection.newQuery()
                .select(columns)
                .from(table)
                .where(uidPredicate(table, uid))
                .fetchOne();
        return row != null ? buildConnectorObject(row, selectedAttributes) : null;
    }

    protected ConnectorObject requireByUid(
            SqlConnection connection, Uid uid, OperationOptions options, boolean includeAllAttributes) {
        var object = findByUid(connection, uid, options, includeAllAttributes);
        if (object == null) {
            throw new UnknownUidException(uid, objectClass.objectClass());
        }
        return object;
    }

    protected Uid generatedUid(SqlAttributeMapping mapping, Object generatedKey,
                               Path<?> table, Map<Path<?>, Object> assignments) {
        if (generatedKey == null) {
            throw new ConnectorException("Database did not return a generated UID");
        }
        if (mapping instanceof SqlAttributeMapping.SingleColumn singleColumn) {
            var connIdValue = singleColumn.singleValueFromAttribute(generatedKey);
            if (connIdValue == null) {
                throw new ConnectorException("Database returned a null generated UID");
            }
            return new Uid(connIdValue.toString());
        }
        if (mapping instanceof SqlAttributeMapping.MultiColumn multiColumn) {
            var uid = new StringBuilder(connIdPart(multiColumn.mainColumn(), generatedKey));
            for (var additionalColumn : multiColumn.additionalColumns()) {
                var path = additionalColumn.dslPath(table);
                if (!assignments.containsKey(path)) {
                    throw new ConnectorException(
                            "Generated composite UID requires a value for column " + path);
                }
                uid.append(multiColumn.delimiter())
                        .append(connIdPart(additionalColumn, assignments.get(path)));
            }
            return new Uid(uid.toString());
        }
        throw new ConnectorException("Unsupported UID mapping " + mapping.getClass().getName());
    }

    protected Object generatedKey(SQLInsertClause insert, Path<?> path) {
        return executeWithKey(insert, path);
    }

    protected void setAssignments(SQLInsertClause insert, Map<Path<?>, Object> assignments) {
        assignments.forEach((path, value) -> set(insert, path, value));
    }

    protected void setAssignments(SQLUpdateClause update, Map<Path<?>, Object> assignments) {
        assignments.forEach((path, value) -> set(update, path, value));
    }

    protected <T> T inTransaction(String action, TransactionWork<T> work) {
        try (var connection = context.getConnection()) {
            try {
                connection.setAutoCommit(false);
                var result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
                throw translate(action, e);
            }
        } catch (RuntimeException e) {
            throw translate(action, e);
        }
    }

    protected InvalidAttributeValueException invalid(String message) {
        return new InvalidAttributeValueException(message);
    }

    private String connIdPart(SqlAttributeMapping.SingleColumn column, Object sqlValue) {
        var value = column.singleValueFromAttribute(sqlValue);
        if (value == null) {
            throw new ConnectorException("Database returned a null UID component");
        }
        return value.toString();
    }

    private SqlAttributeDefinition requireAttribute(String name) {
        var definition = objectClass.attributeFromConnIdName(name);
        if (definition == null) {
            definition = objectClass.attributes().stream()
                    .filter(candidate -> candidate.connId().getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }
        if (definition == null || definition.sql() == null) {
            throw invalid("Unknown or unmapped attribute " + name);
        }
        return definition;
    }

    private boolean mapsSameColumns(
            SqlAttributeDefinition first, SqlAttributeDefinition second) {
        if (first.sql() == null || second.sql() == null) {
            return false;
        }
        var table = tablePath();
        return new LinkedHashSet<>(first.sql().selectPaths(table))
                .equals(new LinkedHashSet<>(second.sql().selectPaths(table)));
    }

    private void addAssignments(Map<Path<?>, Object> assignments, SqlAttributeDefinition definition,
                                Object value, RelationalPathBase<?> table) {
        for (var columnValue : definition.sql().columnValues(table, value)) {
            var path = columnValue.path();
            if (assignments.containsKey(path)
                    && !Objects.deepEquals(assignments.get(path), columnValue.value())) {
                throw invalid("Conflicting values for SQL column " + path);
            }
            assignments.put(path, columnValue.value());
        }
    }

    private Object singleValue(String attributeName, List<Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw invalid("SQL attribute " + attributeName + " must have at most one value");
        }
        return values.getFirst();
    }

    private RuntimeException translate(String action, Throwable failure) {
        if (failure instanceof ConnectorException connectorException) {
            return connectorException;
        }
        if (failure instanceof IllegalArgumentException illegalArgumentException) {
            return new InvalidAttributeValueException(
                    action + " failed: " + illegalArgumentException.getMessage(), illegalArgumentException);
        }

        var sqlException = findSqlException(failure);
        if (sqlException != null) {
            var sqlState = sqlException.getSQLState();
            var message = action + " failed: " + sqlException.getMessage();
            if (isDuplicate(sqlException)) {
                return new AlreadyExistsException(message, failure);
            }
            if (sqlState != null && (sqlState.startsWith("22") || sqlState.startsWith("23"))) {
                return new InvalidAttributeValueException(message, failure);
            }
            if (sqlState != null && sqlState.startsWith("08")) {
                return new ConnectionFailedException(message, failure);
            }
        }
        return new ConnectorException(action + " failed: " + failure.getMessage(), failure);
    }

    private SQLException findSqlException(Throwable failure) {
        for (var current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException) {
                return sqlException;
            }
        }
        return null;
    }

    private boolean isDuplicate(SQLException exception) {
        return "23505".equals(exception.getSQLState())
                || exception.getErrorCode() == 1
                || exception.getErrorCode() == 1062
                || exception.getErrorCode() == 2601
                || exception.getErrorCode() == 2627;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void set(SQLInsertClause insert, Path<?> path, Object value) {
        if (value == null) {
            insert.setNull((Path) path);
        } else {
            insert.set((Path) path, value);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void set(SQLUpdateClause update, Path<?> path, Object value) {
        if (value == null) {
            update.setNull((Path) path);
        } else {
            update.set((Path) path, value);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object executeWithKey(SQLInsertClause insert, Path<?> path) {
        return insert.executeWithKey((Path) path);
    }

    @FunctionalInterface
    protected interface TransactionWork<T> {
        T execute(SqlConnection connection) throws Exception;
    }
}
