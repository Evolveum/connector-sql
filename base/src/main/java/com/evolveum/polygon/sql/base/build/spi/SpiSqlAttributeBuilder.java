package com.evolveum.polygon.sql.base.build.spi;

import com.evolveum.polygon.conndev.build.spi.SpiAttributeBuilder;
import com.evolveum.polygon.conndev.concepts.DefinitionValue;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeBuilder;
import com.evolveum.polygon.sql.base.build.api.SqlAttributeDefinition;
import com.evolveum.polygon.sql.base.build.api.SqlTypeSpecification;
import com.evolveum.polygon.sql.base.connection.SqlValueMapping;

public interface SpiSqlAttributeBuilder<B extends SqlAttributeBuilder<B>> extends SpiAttributeBuilder<B, SqlAttributeDefinition> {


    interface  SqlMapping {

        SqlAttributeBuilder.SqlMapping column(DefinitionValue<String> name);
        SqlAttributeBuilder.SqlMapping type(DefinitionValue<SqlTypeSpecification> typeSpecification);
        SqlAttributeBuilder.SqlMapping notNull(DefinitionValue<Boolean> notNull);
        SqlAttributeBuilder.SqlMapping unique(DefinitionValue<Boolean> unique);
        SqlAttributeBuilder.SqlMapping autoIncrement(DefinitionValue<Boolean> value);
        SqlAttributeBuilder.SqlMapping primaryKey(DefinitionValue<Boolean> primaryKey);

        SqlAttributeBuilder.SqlMapping valueMapping(DefinitionValue<SqlValueMapping> detected);
    }

}

