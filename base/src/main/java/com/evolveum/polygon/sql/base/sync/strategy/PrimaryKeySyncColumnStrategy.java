/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.sync.strategy;

import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeMapping;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * Sync column strategy that uses the UID (primary key) column.
 *
 * <p>Locates the ConnId UID attribute (the one mapped to
 * {@link Uid#NAME}), then extracts its SQL column name.
 * Useful when the table has auto-increment PK used as sync cursor.</p>
 */
public class PrimaryKeySyncColumnStrategy implements SyncColumnStrategy {

    @Override
    public String resolve(SqlObjectClassDefinition def) {
        for (SqlAttributeDefinition attr : def.attributes()) {
            var info = attr.connId();
            if (info == null || !Uid.NAME.equals(info.getName())) {
                continue;
            }
            // Found the UID attribute — get its SQL column
            var sqlMap = attr.sql();
            if (sqlMap instanceof SqlAttributeMapping.SingleColumn sc) {
                return sc.column().value();
            }
            if (sqlMap instanceof SqlAttributeMapping.MultiColumn mc) {
                return mc.mainColumn().column().value();
            }
        }
        return null;
    }
}