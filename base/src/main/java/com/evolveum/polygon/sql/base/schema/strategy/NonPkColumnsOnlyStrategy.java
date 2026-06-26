package com.evolveum.polygon.sql.base.schema.strategy;

import com.evolveum.polygon.sql.base.schema.AttributeDetectionStrategy;
import com.evolveum.polygon.sql.base.schema.SqlColumnMeta;
import com.evolveum.polygon.sql.base.schema.SqlTableInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Strategy that filters out primary key columns from the attribute list.
 * Useful when PK columns should be excluded from ConnId attributes.
 */
public class NonPkColumnsOnlyStrategy implements AttributeDetectionStrategy {

    private final AttributeDetectionStrategy delegate;

    public NonPkColumnsOnlyStrategy() {
        this.delegate = new DefaultDetectionStrategy();
    }

    public NonPkColumnsOnlyStrategy(AttributeDetectionStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<SqlColumnMeta> detectColumns(SqlTableInfo table) {
        return delegate.detectColumns(table).stream()
                .filter(c -> !c.isPrimaryKey())
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SqlColumnMeta> resolveUid(SqlTableInfo table) {
        return delegate.resolveUid(table);
    }

    @Override
    public boolean isEmbedded(SqlTableInfo table) {
        return delegate.isEmbedded(table);
    }
}