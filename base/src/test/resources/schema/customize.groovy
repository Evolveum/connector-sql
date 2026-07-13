/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
// Schema definition for SqlSchemaCustomizationIntegrationTest
// Maps SQL tables to custom ConnId object class names with remapped attributes
// Uses the unified builder DSL

// Maps table "app_user" → ConnId object class "Person"
objectClass("Person") {
    sql { table "app_user" }

    attribute("user_id") {
        connId { name "__UID__" }
    }
    attribute("user_name") {
        connId { name "__NAME__" }
    }

    // Override attribute mapped from "user_email" SQL column
    attribute("user_email") {
        connId { name "emailAddress" }
    }

    // Custom attribute "loginCount" (not on the SQL table, name serves as both)
    attribute("loginCount") { }
}

// Maps table "app_group" → ConnId object class "Team"
objectClass("Team") {
    sql { table "app_group" }

    attribute("group_id") {
        connId { name "__UID__" }
    }
    attribute("group_name") {
        connId { name "__NAME__" }
    }
}