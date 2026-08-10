objectClass("User") {
    search {

        attributeResolver {
            // rovnako ako attribute resolvery pre SCIMREST
        }

        custom {
            //  rovnake ako custom pre SCIMREST
        }

        sql {
            builtIn {
                enabled true // Framework default
                emptyFilterSupported true // Framework default
                anyFilterSupported true // Framework default
            }
        }
    }
}