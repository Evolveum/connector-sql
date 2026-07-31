/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
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
    private final Boolean readOnly;

    public SqlObjectClassDefinition(ObjectClassInfo connId,
                                    Map<String, SqlAttributeDefinition> nativeAttrs,
                                    Map<String, SqlAttributeDefinition> connIdAttrs,
                                    SqlSchemaBuilderImpl.SqlObjectClassMapping sql,
                                    Boolean readOnly) {
        super(connId, nativeAttrs, connIdAttrs);
        this.sql = sql;
        this.readOnly = readOnly;
    }

    /**
     * Returns whether this object class is marked as read-only.
     * When true, Create/Update/Delete operations are not supported.
     *
     * @return true if read-only, false or null otherwise
     */
    public Boolean getReadOnly() {
        return readOnly;
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
