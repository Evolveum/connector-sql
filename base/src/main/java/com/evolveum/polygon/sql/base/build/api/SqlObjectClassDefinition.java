package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.dev.ConnDevObjectClass;
import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;

import java.util.ArrayList;
import java.util.Map;


public class SqlObjectClassDefinition extends BaseObjectClassDefinition<SqlAttributeDefinition> {

    private final SqlSchemaBuilderImpl.SqlObjectClassMapping sql;

    public SqlObjectClassDefinition(ObjectClassInfo connId,
                                    Map<String, SqlAttributeDefinition> nativeAttrs,
                                    Map<String, SqlAttributeDefinition> connIdAttrs, SqlSchemaBuilderImpl.SqlObjectClassMapping sql) {
        super(connId, nativeAttrs, connIdAttrs);
        this.sql = sql;
    }

    /**
     * Returns the SQL object class mapping for this definition (table/schema), set at construction
     * time from the builder's own {@code sql{}} block.
     *
     * @return the {@link SqlSchemaBuilderImpl.SqlObjectClassMapping}, or null if none was set
     */
    public SqlSchemaBuilderImpl.SqlObjectClassMapping sql() {
        return this.sql;
    }

    @Override
    public void contribute(ConnDevObjectClass target) {
        if (sql == null) {
            return;
        }
        var attributes = new ArrayList<Attribute>();
        if (sql.table() != null && sql.table().value() != null) {
            attributes.add(AttributeBuilder.build("table", sql.table().value()));
        }
        if (sql.schema() != null && sql.schema().value() != null) {
            attributes.add(AttributeBuilder.build("schema", sql.schema().value()));
        }
        if (!attributes.isEmpty()) {
            target.protocolSpecific("sql", attributes);
        }
    }

}
