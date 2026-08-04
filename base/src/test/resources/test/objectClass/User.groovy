objectClass("User") {
    sql {
        table "app_user"
    }

    attribute("id") {
        connId {
            name UID
        }
        sql {
            type INT
            primaryKey
            autoIncrement
        }
    }

    attribute("username") {
        sql {
            type VARCHAR(50)
            notNull true
        }
    }
}
