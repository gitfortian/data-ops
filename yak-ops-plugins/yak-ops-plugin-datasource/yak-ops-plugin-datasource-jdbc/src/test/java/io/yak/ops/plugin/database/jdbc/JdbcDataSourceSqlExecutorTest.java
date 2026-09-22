package io.yak.ops.plugin.database.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.spi.datasource.execution.DataSourceSqlRequest;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class JdbcDataSourceSqlExecutorTest {

  @Test
  void reusesOneConnectionAndCommitsExplicitTransaction() throws Exception {
    Connection connection = mock(Connection.class);
    Statement first = updateStatement(1);
    Statement second = updateStatement(2);
    when(connection.createStatement()).thenReturn(first, second);
    AtomicInteger opened = new AtomicInteger();

    JdbcDataSourceSqlExecutor executor = new JdbcDataSourceSqlExecutor(
        properties(),
        5,
        (ignored, timeout) -> {
          opened.incrementAndGet();
          return connection;
        });

    assertTrue(executor.supportsTransactions());
    executor.beginTransaction();
    assertEquals(1L, executor.execute(new DataSourceSqlRequest("update a set v = 1", 10, 30)).affectedRows());
    assertEquals(2L, executor.execute(new DataSourceSqlRequest("update b set v = 2", 10, 30)).affectedRows());
    executor.commitTransaction();

    assertEquals(1, opened.get());
    verify(connection).setAutoCommit(false);
    verify(connection).commit();
    verify(connection, never()).rollback();
    verify(connection).close();
    verify(first).close();
    verify(second).close();
  }

  @Test
  void rollsBackAndClosesExplicitTransaction() throws Exception {
    Connection connection = mock(Connection.class);
    Statement statement = updateStatement(1);
    when(connection.createStatement()).thenReturn(statement);
    AtomicInteger opened = new AtomicInteger();

    JdbcDataSourceSqlExecutor executor = new JdbcDataSourceSqlExecutor(
        properties(),
        5,
        (ignored, timeout) -> {
          opened.incrementAndGet();
          return connection;
        });

    executor.beginTransaction();
    executor.execute(new DataSourceSqlRequest("delete from demo", 10, 30));
    executor.rollbackTransaction();

    assertEquals(1, opened.get());
    verify(connection).setAutoCommit(false);
    verify(connection).rollback();
    verify(connection, never()).commit();
    verify(connection).close();
  }

  @Test
  void keepsAutoCommitExecutionsOnFreshConnections() throws Exception {
    Connection firstConnection = mock(Connection.class);
    Connection secondConnection = mock(Connection.class);
    Statement firstStatement = updateStatement(1);
    Statement secondStatement = updateStatement(1);
    when(firstConnection.createStatement()).thenReturn(firstStatement);
    when(secondConnection.createStatement()).thenReturn(secondStatement);
    AtomicInteger opened = new AtomicInteger();

    JdbcDataSourceSqlExecutor executor = new JdbcDataSourceSqlExecutor(
        properties(),
        5,
        (ignored, timeout) -> opened.getAndIncrement() == 0 ? firstConnection : secondConnection);

    executor.execute(new DataSourceSqlRequest("update a set v = 1", 10, 30));
    executor.execute(new DataSourceSqlRequest("update b set v = 2", 10, 30));

    assertEquals(2, opened.get());
    verify(firstConnection, never()).setAutoCommit(false);
    verify(secondConnection, never()).setAutoCommit(false);
    verify(firstConnection).close();
    verify(secondConnection).close();
  }

  private static Statement updateStatement(int affectedRows) throws Exception {
    Statement statement = mock(Statement.class);
    when(statement.execute(anyString())).thenReturn(false);
    when(statement.getUpdateCount()).thenReturn(affectedRows);
    return statement;
  }

  private static JdbcConnectionProperties properties() {
    return new JdbcConnectionProperties(
        null,
        "jdbc:test",
        "example.Driver",
        null,
        null,
        null,
        null,
        Map.of(),
        "{}");
  }
}
