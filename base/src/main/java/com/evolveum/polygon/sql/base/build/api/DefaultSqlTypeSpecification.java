package com.evolveum.polygon.sql.base.build.api;

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

    public static SqlTypeSpecification intType() {
        return new DefaultSqlTypeSpecification("INT", null);
    }

    public static SqlTypeSpecification timestampType() {
        return new DefaultSqlTypeSpecification("TIMESTAMP", null);
    }

    public static SqlTypeSpecification varcharType(int size) {
        return new DefaultSqlTypeSpecification("VARCHAR", size);
    }
}