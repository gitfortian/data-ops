package io.yak.ops.business.workflow.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WorkflowRuntimeSingleMasterGuardTest {

  @Test
  void acquiresAndReleasesMysqlNamedLock() throws Exception {
    Fixtures fixtures = fixtures(1);
    WorkflowRuntimeSingleMasterGuard guard =
        new WorkflowRuntimeSingleMasterGuard(fixtures.dataSource, true);

    guard.acquire();
    guard.release();

    verify(fixtures.lockStatement).setString(org.mockito.ArgumentMatchers.eq(1), anyString());
    verify(fixtures.releaseStatement).setString(org.mockito.ArgumentMatchers.eq(1), anyString());
    verify(fixtures.connection).close();
  }

  @Test
  void failsFastWhenAnotherRuntimeOwnsTheLock() throws Exception {
    Fixtures fixtures = fixtures(0);
    WorkflowRuntimeSingleMasterGuard guard =
        new WorkflowRuntimeSingleMasterGuard(fixtures.dataSource, true);

    assertThrows(IllegalStateException.class, guard::acquire);
    verify(fixtures.connection).close();
  }

  private Fixtures fixtures(int acquired) throws Exception {
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    PreparedStatement databaseStatement = mock(PreparedStatement.class);
    PreparedStatement lockStatement = mock(PreparedStatement.class);
    PreparedStatement releaseStatement = mock(PreparedStatement.class);
    ResultSet databaseResult = mock(ResultSet.class);
    ResultSet lockResult = mock(ResultSet.class);
    ResultSet releaseResult = mock(ResultSet.class);

    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement("SELECT DATABASE()")).thenReturn(databaseStatement);
    when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(lockStatement);
    when(connection.prepareStatement("SELECT RELEASE_LOCK(?)")).thenReturn(releaseStatement);
    when(connection.isClosed()).thenReturn(false);

    when(databaseStatement.executeQuery()).thenReturn(databaseResult);
    when(databaseResult.next()).thenReturn(true);
    when(databaseResult.getString(1)).thenReturn("yak_ops");

    when(lockStatement.executeQuery()).thenReturn(lockResult);
    when(lockResult.next()).thenReturn(true);
    when(lockResult.getObject(1, Integer.class)).thenReturn(acquired);

    when(releaseStatement.executeQuery()).thenReturn(releaseResult);
    when(releaseResult.next()).thenReturn(true);
    when(releaseResult.getObject(1, Integer.class)).thenReturn(1);

    return new Fixtures(dataSource, connection, lockStatement, releaseStatement);
  }

  private record Fixtures(
      DataSource dataSource,
      Connection connection,
      PreparedStatement lockStatement,
      PreparedStatement releaseStatement) {
  }
}
