/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTableAccess;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.evolveum.polygon.sql.base.schema.SqlJunctionJoinConfig;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Coordinates related-table handlers inside the root object's JDBC transaction.
 *
 * <p>Conndev's generic strategies cannot currently sequence parent and child writes on one
 * connection, so this coordinator performs only the SQL-specific grouping and ordering.</p>
 */
final class SqlRelatedAttributeOperationCoordinator {

    private final SqlBaseContext context;
    private final SqlWriteOperationSupport rootSupport;
    private final Map<String, SqlChildTableWriteHandler> childHandlers;
    private final List<SqlJunctionJoinConfig> junctions;

    SqlRelatedAttributeOperationCoordinator(
            SqlBaseContext context, SqlObjectClassDefinition objectClass,
            SqlWriteOperationSupport rootSupport) {
        this.context = context;
        this.rootSupport = rootSupport;
        this.childHandlers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (var config : objectClass.relatedAttributeJoinConfigs()) {
            var previous = childHandlers.put(
                    config.targetAttributeName(), new SqlChildTableWriteHandler(context, config));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Multiple child-table handlers target attribute "
                                + config.targetAttributeName());
            }
        }
        this.junctions = objectClass.junctionJoinConfigs();
    }

    boolean supports(String attributeName) {
        return childHandlers.containsKey(attributeName);
    }

    void create(
            SqlConnection connection, Uid uid, Collection<Attribute> attributes) {
        var grouped = groupAttributes(attributes);
        if (grouped.isEmpty()) {
            return;
        }
        var parentValues = parentValues(connection, uid, grouped.keySet());
        grouped.forEach((handler, supported) ->
                handler.create(connection, parentValues, supported));
    }

    void update(
            SqlConnection connection, Uid uid, Collection<AttributeDelta> modifications) {
        var grouped = groupModifications(modifications);
        if (grouped.isEmpty()) {
            return;
        }
        var parentValues = parentValues(connection, uid, grouped.keySet());
        grouped.forEach((handler, supported) ->
                handler.update(connection, parentValues, supported));
    }

    void delete(SqlConnection connection, Uid uid) {
        var parentColumns = new LinkedHashSet<String>();
        childHandlers.values().forEach(handler -> handler.config().joinKeys().stream()
                .map(key -> key.parentColumn())
                .forEach(parentColumns::add));
        junctions.forEach(config -> config.parentJoinKeys().stream()
                .map(key -> key.parentColumn())
                .forEach(parentColumns::add));
        if (parentColumns.isEmpty()) {
            return;
        }

        var parentValues = rootSupport.parentColumnValues(connection, uid, parentColumns);
        childHandlers.values().forEach(handler -> handler.delete(connection, parentValues));
        for (var config : junctions) {
            var junction = new SqlTableAccess(context, config.junctionTable(), "jd");
            var criteria = new LinkedHashMap<String, Object>();
            for (var key : config.parentJoinKeys()) {
                criteria.put(junction.actualColumn(key.childColumn()),
                        junction.toWireValue(
                                key.childColumn(), parentValues.get(key.parentColumn())));
            }
            junction.delete(connection, criteria);
        }
    }

    private Map<SqlChildTableWriteHandler, List<Attribute>> groupAttributes(
            Collection<Attribute> attributes) {
        var result = new LinkedHashMap<SqlChildTableWriteHandler, List<Attribute>>();
        if (attributes == null) {
            return result;
        }
        for (var attribute : attributes) {
            var handler = childHandlers.get(attribute.getName());
            if (handler != null) {
                result.computeIfAbsent(handler, ignored -> new ArrayList<>()).add(attribute);
            }
        }
        return result;
    }

    private Map<SqlChildTableWriteHandler, List<AttributeDelta>> groupModifications(
            Collection<AttributeDelta> modifications) {
        var result = new LinkedHashMap<SqlChildTableWriteHandler, List<AttributeDelta>>();
        if (modifications == null) {
            return result;
        }
        for (var modification : modifications) {
            var handler = childHandlers.get(modification.getName());
            if (handler != null) {
                result.computeIfAbsent(handler, ignored -> new ArrayList<>()).add(modification);
            }
        }
        return result;
    }

    private Map<String, Object> parentValues(
            SqlConnection connection, Uid uid,
            Collection<SqlChildTableWriteHandler> handlers) {
        var columns = new LinkedHashSet<String>();
        handlers.forEach(handler -> handler.config().joinKeys().stream()
                .map(key -> key.parentColumn())
                .forEach(columns::add));
        return rootSupport.parentColumnValues(connection, uid, columns);
    }
}
