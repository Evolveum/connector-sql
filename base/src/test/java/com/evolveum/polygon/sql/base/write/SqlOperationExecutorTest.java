/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.write;

import com.evolveum.polygon.sql.base.SqlBaseContext;
import com.evolveum.polygon.sql.base.SqlConnectorConfiguration;
import com.evolveum.polygon.sql.base.build.api.SqlObjectClassDefinition;
import com.evolveum.polygon.sql.base.connection.SqlConnection;
import com.querydsl.sql.SQLTemplates;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectionFailedException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the SQL adapter, including JDBC failures wrapped by the shared executor. */
public class SqlOperationExecutorTest {

    @Test
    public void commitsClosesAndProvidesFreshScopedConnections() throws Exception {
        var context = new RecordingContext();
        var executor = executor(context, false);
        var first = executor.execute(scope -> {
            assertThat(scope.get(SqlBaseContext.class)).isSameAs(context);
            var sql = scope.get(SqlWriteContext.class);
            assertThat(scope.get(SqlWriteContext.class)).isSameAs(sql);
            assertThat(sql.connection()).isSameAs(context.lastConnection);
            return sql;
        });
        assertThat(first.connection().getConnection().isClosed()).isTrue();
        var second = executor.execute(scope -> scope.get(SqlWriteContext.class));
        assertThat(second).isNotSameAs(first);
        assertThat(second.connection()).isNotSameAs(first.connection());
        assertThat(second.connection().getConnection().isClosed()).isTrue();
        assertThat(context.events).containsExactly(
                "open", "begin", "commit", "close", "open", "begin", "commit", "close");
    }

    @Test
    public void rollsBackWithoutReplacingClassifiedFailure() {
        var context = new RecordingContext();
        var failure = new InvalidAttributeValueException("invalid child");
        assertThatThrownBy(() -> executor(context, false).execute(scope -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(context.events).containsExactly("open", "begin", "rollback", "close");
    }

    @Test
    public void rollsBackOnErrorAsWellAsException() {
        var context = new RecordingContext();
        var failure = new AssertionError("failed work");
        assertThatThrownBy(() -> executor(context, false).execute(scope -> {
            throw failure;
        })).isSameAs(failure);
        assertThat(context.events).containsExactly("open", "begin", "rollback", "close");
    }

    @DataProvider
    public Object[][] sqlFailures() {
        return new Object[][] {
                { "23505", AlreadyExistsException.class },
                { "23502", InvalidAttributeValueException.class },
                { "22001", InvalidAttributeValueException.class },
                { "08006", ConnectionFailedException.class }
        };
    }

    @Test(dataProvider = "sqlFailures")
    public void translatesWrappedJdbcFailures(String state, Class<? extends Exception> type) {
        var context = new RecordingContext();
        var failure = new SQLException("JDBC failure", state);
        assertThatThrownBy(() -> executor(context, false).execute(scope -> {
            throw failure;
        })).isInstanceOf(type).hasRootCause(failure);
        assertThat(context.events).containsExactly("open", "begin", "rollback", "close");
    }

    @Test
    public void rollsBackCommitFailureAndRetainsRollbackFailure() {
        var context = new RecordingContext();
        context.commitFailure = new SQLException("commit failed", "08006");
        context.rollbackFailure = new SQLException("rollback failed");
        assertThatThrownBy(() -> executor(context, false).execute(scope -> "done"))
                .isInstanceOf(ConnectionFailedException.class)
                .hasRootCause(context.commitFailure);
        assertThat(context.commitFailure.getSuppressed()).containsExactly(context.rollbackFailure);
        assertThat(context.events).containsExactly("open", "begin", "commit", "rollback", "close");
    }

    @Test
    public void closesConnectionWhenTransactionCannotBegin() throws Exception {
        var context = new RecordingContext();
        context.beginFailure = new SQLException("begin failed", "08006");
        assertThatThrownBy(() -> executor(context, false).execute(scope -> {
            throw new AssertionError("Work must not run");
        })).isInstanceOf(ConnectionFailedException.class).hasRootCause(context.beginFailure);
        assertThat(context.events).containsExactly("open", "begin", "close");
        assertThat(context.lastConnection.getConnection().isClosed()).isTrue();
    }

    @Test
    public void rejectsReadOnlyObjectsBeforeOpeningConnection() {
        var context = new RecordingContext();
        assertThatThrownBy(() -> executor(context, true).execute(scope -> "unexpected"))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("read-only");
        assertThat(context.events).isEmpty();
    }

    private SqlOperationExecutor executor(RecordingContext context, boolean readOnly) {
        var info = new ObjectClassInfoBuilder();
        info.setType("users");
        var definition = new SqlObjectClassDefinition(
                info.build(), Map.of(), Map.of(), null, readOnly, List.of(), List.of());
        return new SqlOperationExecutor(context, new SqlWriteOperationSupport(context, definition),
                "Test users", List.of());
    }

    private static final class RecordingContext extends SqlBaseContext {

        private final List<String> events = new ArrayList<>();
        private SQLException beginFailure;
        private SQLException commitFailure;
        private SQLException rollbackFailure;
        private SqlConnection lastConnection;

        private RecordingContext() {
            super(new SqlConnectorConfiguration());
        }

        @Override
        public SqlConnection getConnection() {
            events.add("open");
            try {
                lastConnection = new SqlConnection(
                        DriverManager.getConnection("jdbc:h2:mem:executor_" + System.nanoTime()),
                        SQLTemplates.DEFAULT) {
                    @Override
                    public void setAutoCommit(boolean autoCommit) throws SQLException {
                        events.add("begin");
                        if (beginFailure != null) {
                            throw beginFailure;
                        }
                        super.setAutoCommit(autoCommit);
                    }

                    @Override
                    public void commit() throws SQLException {
                        events.add("commit");
                        if (commitFailure != null) {
                            throw commitFailure;
                        }
                        super.commit();
                    }

                    @Override
                    public void rollback() throws SQLException {
                        events.add("rollback");
                        if (rollbackFailure != null) {
                            throw rollbackFailure;
                        }
                        super.rollback();
                    }

                    @Override
                    public void close() {
                        events.add("close");
                        super.close();
                    }
                };
                return lastConnection;
            } catch (SQLException failure) {
                throw new ConnectorException(failure);
            }
        }
    }
}
