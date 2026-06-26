package com.evolveum.polygon.sql.base.api.build;

public class DefaultSqlTypeSpecification extends SqlTypeSpecification {

    private final String typeName;
    private final Integer size;

    private DefaultSqlTypeSpecification(String typeName, Integer size) {
        this.typeName = typeName;
        this.size = size;
    }

    public String getTypeName() {
        return typeName;
    }

    public Integer getSize() {
        return size;
    }

    public static DefaultSqlTypeSpecification intType() {
        return new DefaultSqlTypeSpecification("INT", null);
    }

    public static DefaultSqlTypeSpecification timestampType() {
        return new DefaultSqlTypeSpecification("TIMESTAMP", null);
    }

    public static DefaultSqlTypeSpecification varcharType(int size) {
        return new DefaultSqlTypeSpecification("VARCHAR", size);
    }
}