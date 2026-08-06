// Oracle schema definition for connector integration tests
// All 13 tables: orgchart (3 tables) + directory (10 tables)

// ============================================
// ORGCHART SCHEMA
// ============================================

objectClass("orgchart_type_ref") {
    sql { table "ORGCHART_TYPE_REF" }
    attribute("TYPE_REF_ID") {
        connId { name "__UID__" }
        sql { primaryKey(); type NUMBER(10) }
    }
    attribute("TYPE_CODE") {
        sql { type VARCHAR2(20) }
    }
    attribute("DISPLAY_NAME") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("orgchart_node") {
    sql { table "ORGCHART_NODE" }
    attribute("UNIT_ID") {
        connId { name "__UID__" }
        sql { primaryKey(); type NUMBER(10) }
    }
    attribute("UNIT_CODE") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(100) }
    }
    attribute("PARENT_UNIT_ID") {
        sql { type NUMBER(10) }
    }
    attribute("TYPE_REF_ID") {
        sql { type NUMBER(10) }
    }
    attribute("HIERARCHY_LEVEL") {
        sql { type NUMBER(3) }
    }
    attribute("DISPLAY_ORDER") {
        sql { type NUMBER(10) }
    }
    attribute("CREATED_DATE") {
        sql { type TIMESTAMP(6) }
    }
    attribute("UPDATED_DATE") {
        sql { type TIMESTAMP(6) }
    }
}

objectClass("orgchart_label") {
    sql { table "ORGCHART_LABEL" }
    attribute("UNIT_ID") {
        connId { name "__UID__" }
        sql { type NUMBER(10) }
    }
    attribute("LANGUAGE") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(10) }
    }
    attribute("LABEL_TEXT") {
        sql { type VARCHAR2(500) }
    }
}

// ============================================
// DIRECTORY SCHEMA
// ============================================

objectClass("dir_status_ref") {
    sql { table "DIR_STATUS_REF" }
    attribute("STATUS_CODE") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(1) }
    }
    attribute("STATUS_MEANING") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(100) }
    }
}

objectClass("dir_archetype_ref") {
    sql { table "DIR_ARCHETYPE_REF" }
    attribute("ARCHETYPE_CODE") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(1) }
    }
    attribute("ARCHETYPE_DESCRIPTION") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("dir_staff_origin_ref") {
    sql { table "DIR_STAFF_ORIGIN_REF" }
    attribute("ORIGIN_CODE") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(20) }
    }
    attribute("ORIGIN_MEANING") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("dir_institution_ref") {
    sql { table "DIR_INSTITUTION_REF" }
    attribute("ORIGIN_CODE") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(20) }
    }
    attribute("INSTITUTION_NAME") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("dir_auth_domain") {
    sql { table "DIR_AUTH_DOMAIN" }
    attribute("DOMAIN_ID") {
        connId { name "__UID__" }
        sql { primaryKey(); type NUMBER(10) }
    }
    attribute("DOMAIN_NAME") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(100) }
    }
}

objectClass("dir_account") {
    sql { table "DIR_ACCOUNT" }
    attribute("ACCOUNT_ID") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(8) }
    }
    attribute("FAMILY_NAME") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(400) }
    }
    attribute("GIVEN_NAME") {
        sql { type VARCHAR2(400) }
    }
    attribute("EMAIL_ADDRESS") {
        sql { type VARCHAR2(320) }
    }
    attribute("STATUS_CODE") {
        sql { type VARCHAR2(1) }
    }
    attribute("ARCHETYPE_CODE") {
        sql { type VARCHAR2(1) }
    }
    attribute("DOMAIN_ID") {
        sql { type NUMBER(10) }
    }
    attribute("EXTERNAL_PERSON_ID") {
        sql { type VARCHAR2(8) }
    }
    attribute("INTERNAL_ID") {
        sql { type VARCHAR2(60) }
    }
    attribute("CREATED_AT") {
        sql { type TIMESTAMP(6) }
    }
    attribute("UPDATED_AT") {
        sql { type TIMESTAMP(6) }
    }
}

objectClass("dir_service") {
    sql { table "DIR_SERVICE" }
    attribute("SERVICE_ID") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(64) }
    }
    attribute("SERVICE_FULL_NAME") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
    attribute("IS_PRIVILEGED") {
        sql { type VARCHAR2(1) }
    }
    attribute("DOMAIN_ID") {
        sql { type NUMBER(10) }
    }
    attribute("DIRECTORATEGENERAL") {
        sql { type VARCHAR2(10) }
    }
    attribute("APP_NAME") {
        sql { type VARCHAR2(256) }
    }
    attribute("ENDPOINT_TIME") {
        sql { type NUMBER(19) }
    }
}

objectClass("dir_membership") {
    sql { table "DIR_MEMBERSHIP" }
    attribute("ACCOUNT_ID") {
        connId { name "__UID__" }
        sql { type VARCHAR2(8) }
    }
    attribute("SERVICE_ID") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(64) }
    }
    attribute("MEMBERSHIP_EXPIRY") {
        sql { type TIMESTAMP(6) }
    }
    attribute("UPDATED_AT") {
        sql { type TIMESTAMP(6) }
    }
}

objectClass("dir_xf_entitlement") {
    sql { table "DIR_XF_ENTITLEMENT" }
    attribute("ACCOUNT_ID") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(8) }
    }
    attribute("IS_ENTITLED") {
        sql { type VARCHAR2(1) }
    }
    attribute("ENDPOINT_TIME") {
        sql { type NUMBER(19) }
    }
}

objectClass("dir_job_watermark") {
    sql { table "DIR_JOB_WATERMARK" }
    attribute("JOB_NAME") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(30) }
    }
    attribute("LAST_CONFIRMED") {
        sql { type TIMESTAMP(6) }
    }
    attribute("OPERATOR") {
        sql { type VARCHAR2(50) }
    }
    attribute("NOTE") {
        sql { type VARCHAR2(500) }
    }
    attribute("PROCESSED_ROWS") {
        sql { type NUMBER(10) }
    }
    attribute("UPDATED_AT") {
        sql { type TIMESTAMP(6) }
    }
}
