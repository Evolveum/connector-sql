/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps between SQL query results and ConnId objects without coupling operation types.
 */
public final class SqlObjectMapper {

    private final SqlObjectClassDefinition objectClass;

    public SqlObjectMapper(SqlObjectClassDefinition objectClass) {
        this.objectClass = objectClass;
    }

    public RelationalPathBase<?> tablePath() {
        return objectClass.sql().pathAlias("o");
    }

    public Map<SqlAttributeDefinition, Collection<Path<?>>> selectColumns(
            Path<?> table, OperationOptions options) {
        Map<SqlAttributeDefinition, Collection<Path<?>>> columns = new LinkedHashMap<>();
        // UID and Name must be always selected
        var uidAttribute = objectClass.attributeFromConnIdName(Uid.NAME);
        columns.put(uidAttribute, uidAttribute.sql().selectPaths(table));

        var nameAttribute = objectClass.attributeFromConnIdName(Name.NAME);
        if (nameAttribute != null && nameAttribute.sql() != null) {
            columns.put(nameAttribute, nameAttribute.sql().selectPaths(table));
        }

        var requestedAttributes = options != null ? options.getAttributesToGet() : null;
        var returnDefaultAttributes = options != null
                ? options.getReturnDefaultAttributes()
                : null;
        var includeDefaults = Boolean.TRUE.equals(returnDefaultAttributes)
                || (requestedAttributes == null && returnDefaultAttributes == null);

        if (includeDefaults) {
            for (var attr : objectClass.attributes()) {
                if (attr.sql() != null && attr.connId().isReturnedByDefault()) {
                    columns.put(attr, attr.sql().selectPaths(table));
                }
            }
        }

        if (requestedAttributes != null) {
            for (var requestedName : requestedAttributes) {
                var attr = attributeFromConnIdName(requestedName);
                if (attr != null && attr.sql() != null) {
                    columns.put(attr, attr.sql().selectPaths(table));
                }
            }
        }

        return columns;
    }

    public ConnectorObject buildConnectorObject(
            RelationalPathBase<?> table, Tuple row,
            Map<SqlAttributeDefinition, Collection<Path<?>>> attributes) {
        var tuple = new SqlTuple(table, row);
        var builder = new ConnectorObjectBuilder();
        builder.setObjectClass(objectClass.objectClass());
        for (var attrEntry : attributes.entrySet()) {
            var attr = attrEntry.getKey();
            var mapping = attr.sql();
            if (mapping != null) {
                var value = mapping.valuesFromObject(tuple);
                builder.addAttribute(attr.attributeOf(value));
            }
        }
        // FIXME: Composite UIDs are already produced by their mapping; derived or
        // emulated NAME mappings may still need explicit computation here.
        return builder.build();
    }

    public List<Path<?>> onlyPaths(
            Map<SqlAttributeDefinition, Collection<Path<?>>> selectedAttributes) {
        return selectedAttributes.values().stream()
                .flatMap(Collection::stream)
                .distinct()
                .toList();
    }

    private SqlAttributeDefinition attributeFromConnIdName(String name) {
        var attribute = objectClass.attributeFromConnIdName(name);
        if (attribute != null) {
            return attribute;
        }
        return objectClass.attributes().stream()
                .filter(candidate -> candidate.connId().getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
