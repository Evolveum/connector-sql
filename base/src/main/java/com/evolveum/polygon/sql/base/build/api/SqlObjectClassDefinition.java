package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.schema.BaseObjectClassDefinition;
import com.evolveum.polygon.sql.base.objectclass.SqlObjectClassMapping;
import com.evolveum.polygon.sql.base.schema.SqlAttributeMapping;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Uid;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SqlObjectClassDefinition extends BaseObjectClassDefinition<SqlAttributeDefinition> {

    private SqlObjectClassMapping sqlMapping;

    public SqlObjectClassDefinition(ObjectClassInfo connId,
                                    java.util.Map<String, SqlAttributeDefinition> nativeAttrs,
                                    java.util.Map<String, SqlAttributeDefinition> connIdAttrs) {
        super(connId, nativeAttrs, connIdAttrs);
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
     * @return the {@link SqlObjectClassMapping}, or null if no locator is set
     */
    public SqlObjectClassMapping sql() {
        var m = this.sqlMapping;
        if (m != null) {
            return m;
        }

        var locator = locator();
        if (locator == null) {
            return null;
        }

        Collection<SqlAttributeDefinition> attrs = attributes();
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }

        // Collect attribute mappings and find UID column
        List<SqlAttributeMapping> mappings = new ArrayList<>();
        String uidSqlColumn = null;
        for (SqlAttributeDefinition attr : attrs) {
            var am = attr.sql();
            mappings.add(am);
            if (Uid.NAME.equals(attr.connId().getName())) {
                uidSqlColumn = am.column().value();
            }
        }

        if (mappings.isEmpty()) {
            return null;
        }

        // Fallback: if no UID attribute, use first attribute's column
        if (uidSqlColumn == null) {
            uidSqlColumn = mappings.getFirst().column().value();
        }
        m = new SqlObjectClassMapping(
                objectClass(),
                locator,
                uidSqlColumn);
        this.sqlMapping = m;
        return m;
    }

    @SuppressWarnings("unchecked")
    void setSqlMapping(SqlObjectClassMapping mapping) {
        this.sqlMapping = mapping;
    }
}
