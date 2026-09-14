/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.evolveum.polygon.conndev.concepts.DevelopmentMode;
import com.evolveum.polygon.conndev.logging.ConnDevLog;
import com.evolveum.polygon.conndev.logging.protocol.SqlProtocolData;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Attaches SQL protocol events to the operation entry currently active on this thread (started by
 * {@code ConnDevLog.runOperation} in {@code ClassHandlerConnectorBase}).
 *
 * <p>The events are emitted through this class's own logger, so the SQL protocol layer can be
 * filtered and leveled independently of the operation handlers, while the shared entry id and
 * sequence numbers keep them correlated with the enclosing operation. When no operation entry is
 * active (e.g. calls outside of a ConnId operation), or when development mode is disabled, the
 * calls are no-ops.
 *
 * <p>Tracing is installed by wrapping the pooled JDBC {@link Connection} in a reflective proxy,
 * so the exact SQL text and bound parameter values are captured as sent to the driver.
 */
public final class SqlProtocolTrace {

    private static final ConnDevLog LOG = ConnDevLog.of(SqlProtocolTrace.class);

    private SqlProtocolTrace() {
    }

    /**
     * Wraps the raw connection in a tracing proxy when development mode is active on the current
     * thread, so that statements prepared from it attach SQL protocol events to the active
     * operation entry.
     *
     * @param raw the pooled JDBC connection
     * @return the tracing proxy, or the raw connection when tracing is inactive
     */
    public static Connection tracing(Connection raw) {
        if (!DevelopmentMode.isEnabled()) {
            return raw;
        }
        return (Connection) Proxy.newProxyInstance(
                SqlProtocolTrace.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(raw));
    }

    private static PreparedStatement tracingStatement(String sql, PreparedStatement raw) {
        return (PreparedStatement) Proxy.newProxyInstance(
                SqlProtocolTrace.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                new PreparedStatementHandler(sql, raw));
    }

    private static Statement tracingStatement(Statement raw) {
        return (Statement) Proxy.newProxyInstance(
                SqlProtocolTrace.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                new StatementHandler(raw));
    }

    private static final class ConnectionHandler implements InvocationHandler {

        private final Connection raw;

        private ConnectionHandler(Connection raw) {
            this.raw = raw;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (("prepareStatement".equals(name) || "prepareCall".equals(name))
                    && args != null && args.length >= 1 && args[0] instanceof String sql) {
                var statement = (PreparedStatement) method.invoke(raw, args);
                return tracingStatement(sql, statement);
            }
            if ("createStatement".equals(name)) {
                var statement = (Statement) method.invoke(raw, args);
                return tracingStatement(statement);
            }
            return method.invoke(raw, args);
        }
    }

    private static final class PreparedStatementHandler implements InvocationHandler {

        private final String sql;
        private final PreparedStatement raw;
        private final Map<String, Object> params = new LinkedHashMap<>();

        private PreparedStatementHandler(String sql, PreparedStatement raw) {
            this.sql = sql;
            this.raw = raw;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (isParameterSetter(name, args)) {
                params.put(paramKey(args), "setNull".equals(name) ? null : normalize(args[1]));
                return method.invoke(raw, args);
            }
            if (isExecution(name)) {
                var executedSql = args != null && args.length == 1 && args[0] instanceof String sqlText
                        ? sqlText : sql;
                emit(executedSql);
                params.clear();
                return method.invoke(raw, args);
            }
            return method.invoke(raw, args);
        }

        private String paramKey(Object[] args) {
            return args[0] instanceof Integer index ? String.valueOf(index) : (String) args[0];
        }

        private void emit(String executedSql) {
            var entry = LOG.currentOperation();
            if (entry != null) {
                // Defensive copy: params may hold explicit nulls (setNull), which the wire
                // format serializes as JSON nulls.
                var bound = params.isEmpty() ? null : new LinkedHashMap<>(params);
                entry.sql(new SqlProtocolData.Query(executedSql, bound));
            }
        }
    }

    private static final class StatementHandler implements InvocationHandler {

        private final Statement raw;

        private StatementHandler(Statement raw) {
            this.raw = raw;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            var name = method.getName();
            if (("execute".equals(name) || "executeUpdate".equals(name)
                    || "executeQuery".equals(name) || "executeLargeUpdate".equals(name))
                    && args != null && args.length == 1 && args[0] instanceof String sql) {
                var entry = LOG.currentOperation();
                if (entry != null) {
                    entry.sql(new SqlProtocolData.Query(sql, null));
                }
            }
            return method.invoke(raw, args);
        }
    }

    /** True for {@code setXxx} binding methods (positional or named), not for statement knobs. */
    private static boolean isParameterSetter(String name, Object[] args) {
        return name.startsWith("set")
                && args != null
                && args.length >= 2
                && (args[0] instanceof Integer || args[0] instanceof String);
    }

    private static boolean isExecution(String name) {
        return "execute".equals(name) || "executeUpdate".equals(name) || "executeQuery".equals(name)
                || "executeLargeUpdate".equals(name) || "executeBatch".equals(name);
    }

    /** Keeps values JSON-serializable and human-readable in the development trace. */
    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }
}
