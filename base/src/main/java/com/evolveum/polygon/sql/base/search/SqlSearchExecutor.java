package com.evolveum.polygon.sql.base.search;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlTuple;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Path;
import com.querydsl.sql.RelationalPathBase;
import org.identityconnectors.framework.common.objects.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SqlSearchExecutor {

    protected final SqlBaseContext context;
    protected final SqlObjectClassDefinition objectClass;

    public SqlSearchExecutor(SqlBaseContext context, SqlObjectClassDefinition objectClass) {
        this.context = context;
        this.objectClass = objectClass;
    }

    protected RelationalPathBase<?> getTablePath() {
        return objectClass.sql().pathAlias("o");
    }

    protected Map<SqlAttributeDefinition, Collection<Path<?>>> selectColumns(Path<?> table, OperationOptions options) {
        Map<SqlAttributeDefinition, Collection<Path<?>>> columns = new HashMap<>();
        // UID and Name must be always selected
        var uidAttribute = objectClass.attributeFromConnIdName(Uid.NAME);
        columns.put(uidAttribute, uidAttribute.sql().selectPaths(table));

        var nameAttribute = objectClass.attributeFromConnIdName(Name.NAME);
        if (nameAttribute.sql() != null) {
            columns.put(nameAttribute, nameAttribute.sql().selectPaths(table));
        }

        for (var attr : objectClass.attributes()) {
            if (attr.sql() != null && attr.connId().isReturnedByDefault()) {
                columns.put(attr, attr.sql().selectPaths(table));
            }
        }

        return columns;
    }

    protected ConnectorObject buildConnectorObject(Tuple row, Map<SqlAttributeDefinition, Collection<Path<?>>> attributes) {
        var tuple = new SqlTuple(getTablePath(), row);
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
        // FIXME: Maybe there is need to compute UID, NAME attributes
        return builder.build();
    }

    protected List<Path<?>> onlyPaths(Map<SqlAttributeDefinition, Collection<Path<?>>> selectedAttributes) {
        return selectedAttributes.values().stream().flatMap(Collection::stream).toList();
    }


}
