# Connector SQL — OpenCode Instructions

## Building & Testing
- `mvn test` — runs all TestNG tests (base + connector)
- `mvn -pl base test` — run only base module tests (faster)
- `mvn package` — builds all modules (including connector assembly)

**Java / version note**:
- Root POM `source=/target`: 21
- `base/pom.xml` `source=/target`: 17

**Tests use TestNG** (not JUnit). Surefire uses the `surefire-testng` engine (defined in root POM).

## Architecture
Multi-module Maven project (ConnId connector implementation):
- **`base/`** (shared library): HikariCP pool, SQL dialects, schema detection, ConnId type mapping
- **`connector/generic/`**: Connector bundle assembly (JAR + dependencies)

Key packages under `base/src/main/java/com/evolveum/polygon/sql/base/`:
| Package | Purpose |
|---|---|
| `.connection/` | HikariCP pool, `SqlQueryExecutor`, dialects, `SqlStatement` builder, type mappings |
| `.schema/` | JDBC metadata detection (`SqlSchemaDetector` → `SqlSchema` → ConnId `Schema`) |
| `.groovy/` | Groovy-based handler loading (schema scripts) |

Dialects: `PostgreSqlDialect` (RETURNING), `MySqlDialect`, `OracleSqlDialect` (FETCH FIRST), `SqliteDialect`, `StandardSqlDialect`.

## Important gotchas
- `SqlSchemaDetector.getTables()` first argument must be **`null`** (not `getJdbcUrl()`). Passing the JDBC URL breaks metadata lookup on H2 and most drivers.
- `SqlHandlerBuilder.create()` is a stub.
- `AbstractGroovySqlConnector.reinitializeOnEachCall` defaults to `true` — pool initializes once per call unless set to `false`.
- Connection auto-close: `SqlConnection` auto-closes by default.
- HikariCP: `getConnectionPool()` → `getConnection()` returns a `SqlConnection` wrapper.
- Default Hikari pool size: 10. Configurable via `SqlConnectorConfiguration.poolSize`.
- H2 test URL: `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1` (keeps DB alive after statements).
- ConnId schema detection requires `autoDiscoverSchema=true` (enabled by default). When enabled, connector initialization automatically populates `SqlSchema`.