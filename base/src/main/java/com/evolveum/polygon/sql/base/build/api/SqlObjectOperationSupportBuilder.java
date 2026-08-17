/*
 * Copyright (c) 2026 Evolveum and contributors
 * 
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 * 
 */
package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.build.api.ObjectOperationSupportBuilder;
import com.evolveum.polygon.conndev.spi.ObjectClassOperation;

public interface SqlObjectOperationSupportBuilder extends ObjectOperationSupportBuilder {

    <T extends ObjectClassOperation> SqlObjectOperationSupportBuilder register(
            Class<T> operationType, T operation);

    /**
     * Disable the Create operation for this object class.
     * Used by detection strategies when the underlying SQL table does not support creates
     * (e.g., views, or when all columns are primary keys).
     */
    SqlObjectOperationSupportBuilder disableCreate();

    /**
     * Disable the Update operation for this object class.
     * Used by detection strategies when the underlying SQL table does not support updates
     * (e.g., views).
     */
    SqlObjectOperationSupportBuilder disableUpdate();

    /**
     * Disable the Delete operation for this object class.
     * Used by detection strategies when the underlying SQL table does not support deletes
     * (e.g., views).
     */
    SqlObjectOperationSupportBuilder disableDelete();
}
