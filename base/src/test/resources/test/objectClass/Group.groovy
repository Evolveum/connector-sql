objectClass("Group") {
    sql {
        table "app_group"
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

    attribute("name") {
        sql {
            type VARCHAR(50)
        }
    }
}
