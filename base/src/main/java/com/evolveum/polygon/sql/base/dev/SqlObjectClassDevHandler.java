/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.dev;

import com.evolveum.polygon.conndev.api.ContextLookup;
import com.evolveum.polygon.conndev.dev.ConnDevObjectClassSerializer;
import com.evolveum.polygon.conndev.spi.ObjectSearchOperation;
import com.evolveum.polygon.sql.base.SqlBaseContext;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;

/**
 * Search handler for the {@code conndev_ObjectClass} object class of the SQL connector. Serializes the
 * framework schema model (conndev {@code BaseSchema}, translated from the detected tables) via the
 * shared {@link ConnDevObjectClassSerializer}. Supports equals filters on {@code __UID__} / {@code __NAME__}.
 */
public class SqlObjectClassDevHandler implements ObjectSearchOperation {

    private final SqlBaseContext context;

    public SqlObjectClassDevHandler(SqlBaseContext context) {
        this.context = context;
    }

    @Override
    public void executeQuery(ContextLookup contextLookup, Filter filter, ResultsHandler resultsHandler,
            OperationOptions operationOptions) {
        var schema = context.schema();
        if (schema == null) {
            return;
        }
        for (var object : ConnDevObjectClassSerializer.serializeAll(schema.objectClasses())) {
            if (!matchesFilter(filter, object)) {
                continue;
            }
            if (!resultsHandler.handle(object)) {
                return;
            }
        }
    }

    private boolean matchesFilter(Filter filter, ConnectorObject object) {
        if (filter == null) {
            return true;
        }
        if (filter instanceof EqualsFilter equalsFilter) {
            var attributeName = equalsFilter.getAttribute().getName();
            var filterValue = AttributeUtil.getSingleValue(equalsFilter.getAttribute());
            if (Uid.NAME.equals(attributeName)) {
                return object.getUid().getUidValue().equals(filterValue);
            }
            if (Name.NAME.equals(attributeName)) {
                return object.getName().getNameValue().equals(filterValue);
            }
        }
        return false;
    }
}
