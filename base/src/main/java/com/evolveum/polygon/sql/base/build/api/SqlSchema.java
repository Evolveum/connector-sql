/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.schema.BaseSchema;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Schema;

import java.util.Map;

public class SqlSchema extends BaseSchema<SqlObjectClassDefinition> {

    /**
     * Constructs a new BaseSchema.
     *
     * @param connIdSchema  the ConnId Schema object
     * @param objectClasses the map of object class definitions
     */
    public SqlSchema(Schema connIdSchema, Map<ObjectClass, SqlObjectClassDefinition> objectClasses) {
        super(connIdSchema, objectClasses);
    }
}
