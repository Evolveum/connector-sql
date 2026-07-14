package com.evolveum.polygon.sql.base.build.api;

import com.evolveum.polygon.conndev.schema.BaseAttributeDefinition;

public class SqlAttributeDefinition extends BaseAttributeDefinition {

    private SqlAttributeMapping sql;

    /**
     * Constructs a {@code SqlAttributeDefinition} from the supplied builder, performing type
     * resolution across protocol mappings and building the final {@link org.identityconnectors.framework.common.objects.AttributeInfo}.
     *
     * @param builder the {@code SqlAttributeBuilderImpl} providing all metadata for this attribute
     * @throws IllegalStateException    if multiple protocol mappings declare conflicting ConnId types
     * @throws IllegalArgumentException if no ConnId type can be resolved for a non-reference attribute
     */
    public SqlAttributeDefinition(SqlAttributeBuilderImpl builder) {
        super(builder);
        this.sql = builder.sql().build();

    }

    /**
     * Returns the SQL attribute mapping for this attribute, constructing it lazily from
     * the definition's own properties the first time it's called.
     * <pre>
     *   connId()      → getConnIdName()
     *   remoteName()  → getSqlColumn()
     *   connId()      → isReturnedByDefault()
     * </pre>
     */
    public SqlAttributeMapping sql() {
        return this.sql;
    }

}
