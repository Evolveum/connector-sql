package h2.basic

// Object class names are by default same as tables name
objectClass("User") {
    sql {
        // Allows to override / use different table name than object class name
        table "User"
    }

    // Attributes names are by default same as table names
    attribute("id") {
        connId {
            // Derived from SQL schema by being non-composite primary key
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
            type VARCHAR(255)
            notNull true
            unique true
        }
    }
    attribute("email") {
        sql {
            type VARCHAR(255)
            unique true
        }
    }
    attribute("created_at") {
        sql {
            type TIMESTAMP
        }
    }

    attribute("address") {
        // Attribute name is derived because UserAddress is embedded in User and contains User as a prefix
        complexType("UserAddress")
        multiValued true
        sql {
            // Probably Join here could be described
            joinOn attribute("id").eq(other.attribute("user_id"))
        }

    }
}

objectClass("UserAddress") {
    /**
     * Address is embedded, because it does has non-null foreign key to user with delete cascade
     */
    embedded true
    sql {
        table "UserAddress"
    }

    attribute("id") {
        sql {
            type INT;
            primaryKey true
            autoIncrement true
        }
    }
    attribute("user_id") {
        sql {
            type INT
            notNull true
            // Simple declaration of foreign key
            foreignKey("User") {
                references "id"
                onDelete CASCADE
            }
        }
    }
    attribute("street") {
        sql {
            type VARCHAR(255)
        }
    }
    attribute("city") {
        sql {
            VARCHAR(255)
        }
    }
    attribute("country") {
        sql {
            VARCHAR(255)
        }
    }
    attribute("primary_flag") {
        sql {
            type BOOLEAN
        }
    }
}