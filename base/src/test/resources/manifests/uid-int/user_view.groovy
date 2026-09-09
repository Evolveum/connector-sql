// Schema with an explicitly typed INT __UID__ (reproduces the int-not-converted-to-string report)
objectClass("user_view") {
    sql {
        table "user_view"
        schema "public"
    }
    readOnly(true)
    onlyExplicitlyListed(true)


    attribute("id") {
        connId { name "__UID__" }
        sql {
            type INT
            primaryKey
        }
    }


    attribute("username") {
        connId { name "__NAME__" }
        sql { type VARCHAR(255) }
    }


    attribute("city") {
        sql { type VARCHAR(255) }
    }


    attribute("country") {
        sql { type VARCHAR(255) }
    }


    attribute("created_at") {
        sql { type TIMESTAMP(6) }
    }


    attribute("email") {
        sql { type VARCHAR(255) }
    }


    attribute("street") {
        sql { type VARCHAR(255) }
    }
}
