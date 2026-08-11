# Custom SQL Search — Custom Query Handler Plan

## Context

ConnId filters cover all normal "attribute-based" search. Custom SQL search is for:
- Row visibility filtering on hidden columns (legacy, soft-delete, tenant isolation, etc.)
- Queries that cannot be expressed as ConnId filters (computed properties, multi-step business logic)
- Different read source than write source (materialized views, DB views with policies)
- Query hints / dialect optimizations


### Phase 1: Built-in handler enhancements

#### 1. `SqlSearchOperationBuilder.java:Builtin` — MODIFY

Add filter support + where predicate to built-in DSL:

```java
interface BuiltIn extends Fluent<BuiltIn> {
    BuiltIn enabled(boolean value);
    BuiltIn emptyFilterSupported(boolean value);
    BuiltIn anyFilterSupported(boolean value);
    BuiltIn supportedFilter(FilterSpecification filterSpec);
    BuiltIn where(@DelegatesTo(...) Closure<BooleanExpression> closure);
    FilterSpecification.Attribute attribute(String name);
}
```

#### 2. `SqlSearchOperationBuilderImpl.java:BuiltInBuilder` — MODIFY

Add fields:
```java
private Set<FilterSpecification> supportedFilters = new HashSet<>();
private Closure<BooleanExpression> whereClosure;
private Boolean emptyFilterSupported;
private boolean anyFilterSupported = true;  // default catch-all
```

`supports(Filter)`:
```java
@Override
public boolean supports(Filter filter) {
    if (filter == null) {
        return emptyFilterSupported != null && emptyFilterSupported;
    }
    if (supportedFilters.isEmpty()) {
        return anyFilterSupported;
    }
    return supportedFilters.stream().anyMatch(a -> a.matches(filter));
}
```

`build()` passes `supportedFilters`, `whereClosure` to `SqlSearchOperation`.

#### 3. `SqlSearchOperation.java` — MODIFY

Accept `Set<FilterSpecification>` + `Closure<BooleanExpression>` in constructor.

`supports(Filter)`: same logic as `BuiltInBuilder.supports()`.

In `executeQuery`:
```java
BooleanExpression predicate = SqlFilterTranslator.translate(objectClass, tablePath, filter);
BooleanExpression where = null;
if (whereClosure != null) {
    var builder = new SqlWherePredicateBuilder(tablePath);
    where = (BooleanExpression) GroovyClosures.copyAndCall(whereClosure, builder);
}
BooleanExpression combined = predicate;
if (where != null) {
    combined = (predicate != null ? predicate.and(where) : where);
}
if (combined != null) {
    query.where(combined);
}
```

#### 4. `SqlWherePredicateBuilder.java` — NEW

Groovy-friendly predicate accumulator for `where` clause.

```java
class SqlWherePredicateBuilder {
    private final RelationalPathBase<?> tablePath;
    private final List<BooleanExpression> predicates = new ArrayList<>();

    // Groovy: e.legacy == false
    public Object getProperty(String name) {
        return new SqlColumn(tablePath, name);
    }

    // After Groovy closure runs, each comparison registers itself
    public BooleanExpression build() {
        return predicates.stream().reduce(BooleanExpression::and).orElse(null);
    }
}
```

`SqlColumn` — inner class wrapping QueryDSL path:
```java
class SqlColumn implements Comparable<Object> {
    // Use QueryDSL's ComparableBase — Groovy == maps to eq()
    private final ComparableBase<Object> path;
    SqlColumn(String name) { path = createPath(tablePath, name); }
}
```

Column type: check `SqlColumnMeta` from schema → if not found → **throw error**.

### Phase 2: Custom query handler

#### 5. `SqlSearchOperationBuilder.java:SqlSpecific` — MODIFY

Add `custom()` method:
```java
SqlCustomSearchOperationBuilder custom();
default SqlCustomSearchOperationBuilder custom(Closure<?> closure) { ... }
```

#### 6. `SqlSearchOperationBuilderImpl.java` — MODIFY

Add `SqlCustomSearchOperationBuilderImpl custom;` field. In `sql()` delegate, lazily create and add to `builders`.

#### 7. `SqlCustomSearchOperationBuilder.java` — NEW

```java
public interface SqlCustomSearchOperationBuilder {
    SqlCustomSearchOperationBuilder supportedFilter(FilterSpecification filterSpec);
    SqlCustomSearchOperationBuilder query(@DelegatesTo(value = SqlCustomQueryBuilderContext.class) Closure<?> closure);
    SqlCustomSearchOperationBuilder emptyFilterSupported(boolean value);
    FilterSpecification.Attribute attribute(String name);
    SqlCustomSearchOperationBuilder sqlValue(Object value);
}
```

#### 8. `SqlCustomSearchOperationBuilderImpl.java` — NEW

```java
class SqlCustomSearchOperationBuilderImpl 
    implements SqlCustomSearchOperationBuilder, FilterAwareSearchProcessorBuilder {
    
    private Set<FilterSpecification> supportedFilters = new HashSet<>();
    private Closure<?> queryClosure;
    private Boolean emptyFilterSupported;
    private SqlBaseContext context;
    private SqlObjectClassDefinition objectClass;
    
    @Override
    public boolean supports(Filter filter) {
        return supportedFilters.stream().anyMatch(a -> a.matches(filter));
    }
    
    @Override
    public FilterAwareExecuteQueryProcessor build() {
        return new SqlCustomSearchOperation(context, objectClass, queryClosure);
    }
}
```

#### 9. `SqlCustomSearchOperation.java` — NEW

Runtime executor:

```java
class SqlCustomSearchOperation implements FilterAwareExecuteQueryProcessor {
    
    @Override
    public void executeQuery(ContextLookup c, Filter filter, ResultsHandler resultsHandler, OperationOptions options) {
        var selectedAttributes = selectColumns(tablePath, options);
        int pageSize = 200;
        int offset = 0;
        
        while (true) {
            var context = new SQLQueryContext(conn, tablePath, filter, objectClass);
            GroovyClosures.copyAndCall(queryClosure, context);
            
            SQLQuery<Tuple> query = context.buildQuery(columns, pageSize, offset);
            var rows = query.fetch();
            
            for (var row : rows) {
                var obj = buildConnectorObject(row, selectedAttributes);
                if (!resultsHandler.handle(obj)) return;
            }
            if (rows.isEmpty() || rows.size() < pageSize) return;
            offset += pageSize;
        }
    }
}
```

#### 10. `SqlCustomQueryBuilderContext.java` — NEW

DSL context for Groovy query closure. Fluent accumulators:

```java
class SqlCustomQueryBuilderContext {
    private final List<Path<?>> selectPaths = new ArrayList<>();
    private SqlTablePath fromTable;
    private List<BooleanExpression> wheres = new ArrayList<>();
    private List<OrderSpecifier<?>> orderBys = new ArrayList<>();
    
    SqlTablePath table(String name, String alias);
    SqlCustomQueryBuilderContext select(Path<?>... paths);
    SqlCustomQueryBuilderContext from(SqlTablePath table);
    SqlCustomQueryBuilderContext where(BooleanExpression pred);
    SqlCustomQueryBuilderContext orderBy(OrderSpecifier<?>... specs);
    Object value();            // filter value via checkOnlyValue
    Object sqlValue(Object v); // ConnId value → SQL wire value (all types)
    Filter filter();           // full ConnId filter
}
```

#### 11. `SqlTablePath.java` — NEW

QueryDSL path with column type inference from `SqlColumnMeta`:

```java
class SqlTablePath extends RelationalPathBase<Object> {
    
    ComparableBase<Object> column(String name) {
        SqlColumnMeta meta = getMeta(name);  // from SqlTableInfo stored in context
        if (meta == null) throw error("Column not found: " + name);
        return createPath(meta.getJavaType());  // StringPath, BooleanPath, etc.
    }
    
    <T> ComparableBase<T> column(String name, Class<T> type) {
        return createExplicitPath(type);  // explicit type override
    }
}
```

If column not found in metadata → **throw error**.

#### 12. `SqlBaseContext.java` — MODIFY

Add `Map<String, SqlTableInfo>` with table lookup:
```java
private Map<String, SqlTableInfo> tableInfoByRef = new HashMap<>();
void tableInfoByRef(Map<...> tables);
SqlTableInfo getTableInfo(String schema, String table);
```

#### 13. `AbstractGroovySqlConnector.java` — MODIFY

After schema detection, populate `context.tableInfoByRef(tables)`.

### Result mapping (custom queries)

After `query.fetch()` returns `List<Tuple>`, for each row:
- For each attribute in `SqlObjectClassDefinition`, look up column name from `attribute.sql().column().value()`
- Extract from `Tuple` by column label (may include alias prefix)
- Use existing `SqlSearchExecutor.buildConnectorObject()` logic

### Usage example

```groovy
objectClass("User") {
    search {
        sql {
            builtIn {
                enabled true
                anyFilterSupported true
                where { e ->
                    e.legacy == false
                    e.status != 'deleted'
                }
            }
            custom {
                supportedFilter(attribute("username").eq().anySingleValue())
                query { q ->
                    def a = q.table("accounts", "a")
                    q.select(a.column("id"), a.column("username"), a.column("email"))
                     .from(a)
                     .where(a.column("status").eq('active'))
                     .where(a.column("username").eq(q.sqlValue(q.value())))
                     .orderBy(a.column("username").asc())
                }
            }
        }
    }
}
```

### Key design decisions

1. **FilterSpecification reused from conndev** — `com.evolveum.polygon.conndev.api.FilterSpecification`
2. **anyFilterSupported = catch-all** — Built-in declares `anyFilterSupported(true)` to handle filters custom doesn't match
3. **sqlValue() covers all ConnId types** — Uses `QueryDslTypeMapping` conversions (String, Boolean, Integer, Long, BigDecimal, Float, Double, ZonedDateTime, Timestamp, Date, LocalTime, byte[])
4. **Row-level column type error** — `SqlTablePath.column(name)` throws if column not in schema-detected metadata
5. **Result mapping uses object class schema** — Column name from `attribute.sql().column().value()` matches result
6. **Approach A for query closure** — Stored as `Closure<?>` prototype, executed fresh at every `executeQuery()` via `GroovyClosures.copyAndCall` (same pattern as SCIMREST's `ScriptedGroovySearchProcessor`)
7. **Pagination handled by connector** — Custom query does NOT include LIMIT/OFFSET; connector appends those after user's query config is replayed
