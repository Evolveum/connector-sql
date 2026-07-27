// Oracle schema definition for connector integration tests
// All 13 tables: orgchart (3 tables) + directory (10 tables)

// ============================================
// ORGCHART SCHEMA
// ============================================

objectClass("orgchart_type_ref") {
    sql { table "ORGCHART_TYPE_REF" }
    attribute("type_ref_id") {
        connId { name "__UID__" }
        sql { primaryKey(); type NUMBER(10) }
    }
    attribute("type_code") {
        sql { type VARCHAR2(20) }
    }
    attribute("display_name") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("orgchart_node") {
    sql { table "ORGCHART_NODE" }
    attribute("unit_id") {
        connId { name "__UID__" }
        sql { primaryKey(); type NUMBER(10) }
    }
    attribute("unit_code") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(100) }
    }
    attribute("parent_unit_id") {
        sql { type NUMBER(10) }
    }
    attribute("type_ref_id") {
        sql { type NUMBER(10) }
    }
    attribute("hierarchy_level") {
        sql { type NUMBER(3) }
    }
    attribute("display_order") {
        sql { type NUMBER(10) }
    }
    attribute("created_date") {
        sql { type TIMESTAMP(6) }
    }
    attribute("updated_date") {
        sql { type TIMESTAMP(6) }
    }
}

objectClass("orgchart_label") {
    sql { table "ORGCHART_LABEL" }
    attribute("unit_id") {
        connId { name "__UID__" }
        sql { type NUMBER(10) }
    }
    attribute("language") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(10) }
    }
    attribute("label_text") {
        sql { type VARCHAR2(500) }
    }
}

// ============================================
// DIRECTORY SCHEMA
// ============================================

objectClass("dir_status_ref") {
    sql { table "DIR_STATUS_REF" }
    attribute("status_code") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(1) }
    }
    attribute("status_meaning") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(100) }
    }
}

objectClass("dir_archetype_ref") {
    sql { table "DIR_ARCHETYPE_REF" }
    attribute("archetype_code") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(1) }
    }
    attribute("archetype_description") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("dir_staff_origin_ref") {
    sql { table "DIR_STAFF_ORIGIN_REF" }
    attribute("origin_code") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(20) }
    }
    attribute("origin_meaning") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("dir_institution_ref") {
    sql { table "DIR_INSTITUTION_REF" }
    attribute("origin_code") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(20) }
    }
    attribute("institution_name") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
}

objectClass("dir_auth_domain") {
    sql { table "DIR_AUTH_DOMAIN" }
    attribute("domain_id") {
        connId { name "__UID__" }
        sql { primaryKey(); type NUMBER(10) }
    }
    attribute("domain_name") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(100) }
    }
}

objectClass("dir_account") {
    sql { table "DIR_ACCOUNT" }
    attribute("account_id") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(8) }
    }
    attribute("family_name") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(400) }
    }
    attribute("given_name") {
        sql { type VARCHAR2(400) }
    }
    attribute("email_address") {
        sql { type VARCHAR2(320) }
    }
    attribute("status_code") {
        sql { type VARCHAR2(1) }
    }
    attribute("archetype_code") {
        sql { type VARCHAR2(1) }
    }
    attribute("domain_id") {
        sql { type NUMBER(10) }
    }
    attribute("external_person_id") {
        sql { type VARCHAR2(8) }
    }
    attribute("internal_id") {
        sql { type VARCHAR2(60) }
    }
    attribute("created_at") {
        sql { type TIMESTAMP(6) }
    }
    attribute("updated_at") {
        sql { type TIMESTAMP(6) }
    }
}

objectClass("dir_service") {
    sql { table "DIR_SERVICE" }
    attribute("service_id") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(64) }
    }
    attribute("service_full_name") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(255) }
    }
    attribute("is_privileged") {
        sql { type VARCHAR2(1) }
    }
    attribute("domain_id") {
        sql { type NUMBER(10) }
    }
    attribute("directorategeneral") {
        sql { type VARCHAR2(10) }
    }
    attribute("app_name") {
        sql { type VARCHAR2(256) }
    }
    attribute("endpoint_time") {
        sql { type NUMBER(19) }
    }
}

objectClass("dir_membership") {
    sql { table "DIR_MEMBERSHIP" }
    attribute("account_id") {
        connId { name "__UID__" }
        sql { type VARCHAR2(8) }
    }
    attribute("service_id") {
        connId { name "__NAME__" }
        sql { type VARCHAR2(64) }
    }
    attribute("membership_expiry") {
        sql { type TIMESTAMP(6) }
    }
    attribute("updated_at") {
        sql { type TIMESTAMP(6) }
    }
}

objectClass("dir_xf_entitlement") {
    sql { table "DIR_XF_ENTITLEMENT" }
    attribute("account_id") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(8) }
    }
    attribute("is_entitled") {
        sql { type VARCHAR2(1) }
    }
    attribute("endpoint_time") {
        sql { type NUMBER(19) }
    }
}

objectClass("dir_job_watermark") {
    sql { table "DIR_JOB_WATERMARK" }
    attribute("job_name") {
        connId { name "__UID__" }
        sql { primaryKey(); type VARCHAR2(30) }
    }
    attribute("last_confirmed") {
        sql { type TIMESTAMP(6) }
    }
    attribute("operator") {
        sql { type VARCHAR2(50) }
    }
    attribute("note") {
        sql { type VARCHAR2(500) }
    }
    attribute("processed_rows") {
        sql { type NUMBER(10) }
    }
    attribute("updated_at") {
        sql { type TIMESTAMP(6) }
    }
}
