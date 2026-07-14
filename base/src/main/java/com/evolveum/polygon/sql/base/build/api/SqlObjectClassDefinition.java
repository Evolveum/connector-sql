package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;


public class SqlObjectClassDefinition extends BaseObjectClassDefinition<SqlAttributeDefinition> {

    private final SqlSchemaBuilderImpl.SqlObjectClassMapping sql;

    public SqlObjectClassDefinition(ObjectClassInfo connId,
                                    java.util.Map<String, SqlAttributeDefinition> nativeAttrs,
                                    java.util.Map<String, SqlAttributeDefinition> connIdAttrs, SqlSchemaBuilderImpl.SqlObjectClassMapping sql) {
        super(connId, nativeAttrs, connIdAttrs);
        this.sql = sql;
    }

    /**
     * Returns the SQL object class mapping for this definition, computing it lazily from
     * the definition's own properties the first time it's called.
     *
     * <p>No parameters required — all data is derived from the definition's {@link #locator()},
     * all attributes' {@link SqlAttributeDefinition#sql()}, and ConnId metadata.
     *
     * <pre>
     *   locator()          → getTableName()
     *   attributes().sql() → list of SqlAttributeMapping
     *   uid NAME → getUidSqlColumn()   (fallback: first attribute's column)
     *   returnedByDefault  → getReturnedByDefaultColumns()
     * </pre>
     *
     * @return the {@link SqlSchemaBuilderImpl.SqlObjectClassMapping}, or null if no locator is set
     */
    public SqlSchemaBuilderImpl.SqlObjectClassMapping sql() {
        return this.sql;
    }

}
