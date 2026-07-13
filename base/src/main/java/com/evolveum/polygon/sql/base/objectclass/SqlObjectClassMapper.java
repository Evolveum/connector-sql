/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.objectclass;

import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges SQL-side table metadata and ConnId-side attribute definitions.
 *
 * @deprecated Use {@link SqlObjectClassDefinition#sql()} directly instead.
 *             This class is retained for backward compatibility.
 */
@Deprecated
public class SqlObjectClassMapper {

    /**
     * Builds mappings for all object classes in the schema.
     * Delegates to {@link SqlObjectClassDefinition#sql()}.
     *
     * @deprecated Use {@link SqlObjectClassDefinition#sql()} directly.
     */
    @Deprecated
    public static Map<ObjectClass, SqlObjectClassMapping> buildAll(SqlSchema schema) {
        if (schema == null) {
            return Map.of();
        }
        Map<ObjectClass, SqlObjectClassMapping> result = new LinkedHashMap<>();
        for (SqlObjectClassDefinition ocDef : schema.objectClasses()) {
            var mapping = ocDef.sql();
            if (mapping != null) {
                result.put(ocDef.objectClass(), mapping);
            }
        }
        return result;
    }
}