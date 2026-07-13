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
