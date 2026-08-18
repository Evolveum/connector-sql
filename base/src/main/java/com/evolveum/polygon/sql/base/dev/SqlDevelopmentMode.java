/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.dev;

import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.Uid;

/** Development-only SQL metadata object classes. */
public final class SqlDevelopmentMode {

    public static final String TABLE_OC_NAME = "conndev_SqlTable";
    public static final String CATALOG_ATTRIBUTE = "catalog";
    public static final String SCHEMA_ATTRIBUTE = "schema";
    public static final String TABLE_TYPE_ATTRIBUTE = "tableType";
    public static final String REMARKS_ATTRIBUTE = "remarks";
    public static final String TABLE_CONTENT_ATTRIBUTE = "tableContent";

    private SqlDevelopmentMode() {
    }

    /** Raw detected table/view metadata, analogous to the SCIM development metadata exports. */
    public static ObjectClassInfo tableObjectClassInfo() {
        var builder = new ObjectClassInfoBuilder();
        builder.setType(TABLE_OC_NAME);
        builder.addAttributeInfo(readOnly(Uid.NAME));
        builder.addAttributeInfo(readOnly(Name.NAME));
        builder.addAttributeInfo(readOnly(CATALOG_ATTRIBUTE));
        builder.addAttributeInfo(readOnly(SCHEMA_ATTRIBUTE));
        builder.addAttributeInfo(readOnly(TABLE_TYPE_ATTRIBUTE));
        builder.addAttributeInfo(readOnly(REMARKS_ATTRIBUTE));
        builder.addAttributeInfo(readOnly(TABLE_CONTENT_ATTRIBUTE));
        return builder.build();
    }

    private static AttributeInfo readOnly(String name) {
        return AttributeInfoBuilder.define(name)
                .setType(String.class)
                .setCreateable(false)
                .setUpdateable(false)
                .build();
    }
}
