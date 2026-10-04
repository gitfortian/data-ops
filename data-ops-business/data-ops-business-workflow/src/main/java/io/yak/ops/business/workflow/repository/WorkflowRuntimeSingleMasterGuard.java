package io.yak.ops.business.workflow.repository;

import io.yak.framework.common.jdbc.JdbcDatabase;
import java.nio.ByteBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * First-phase single-Master guard for the durable workflow runtime.
 *
 * <p>Yak Workflow does not implement distributed Master election yet. A dedicated database session lock
 * makes that deployment boundary explicit: a second Yak Ops instance pointing at the same business
 * database fails fast instead of concurrently recovering and driving the same executions.</p>
 */
@Component
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowRuntimeSingleMasterGuard {
  private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeSingleMasterGuard.class);

  private final DataSource dataSource;
  private final boolean enabled;
  private Connection ownershipConnection;
  private String lockName;
  private boolean postgresql;
  private long advisoryKey;

  public WorkflowRuntimeSingleMasterGuard(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      @Value("${yak.workflow.runtime.single-master-guard-enabled:true}") boolean enabled) {
    this.dataSource = dataSource;
    this.enabled = enabled;
  }

  @PostConstruct
  void acquire() {
    if (!enabled) {
      log.warn("[workflow] single-master runtime guard is disabled");
      return;
    }
    try {
      ownershipConnection = dataSource.getConnection();
      postgresql = JdbcDatabase.isPostgresql(ownershipConnection.getMetaData().getURL());
      lockName = lockName(databaseName(ownershipConnection));
      advisoryKey = ByteBuffer.wrap(sha256(lockName)).getLong();
      Integer acquired = queryInteger(
          ownershipConnection,
          postgresql ? "SELECT CASE WHEN pg_try_advisory_lock(?) THEN 1 ELSE 0 END"
              : "SELECT GET_LOCK(?, 0)",
          lockName);
      if (acquired == null || acquired != 1) {
        closeConnection();
        throw new IllegalStateException(
            "Workflow Runtime 已被其他 Yak Ops 实例占用；第一阶段只支持单 Master：" + lockName);
      }
      log.info("[workflow] acquired single-master runtime lock={}", lockName);
    } catch (SQLException exception) {
      closeConnection();
      throw new IllegalStateException("获取 Workflow Runtime 单 Master 锁失败", exception);
    }
  }

  @PreDestroy
  void release() {
    Connection connection = ownershipConnection;
    if (connection == null) return;
    try {
      if (!connection.isClosed() && lockName != null) {
        queryInteger(connection, postgresql
            ? "SELECT CASE WHEN pg_advisory_unlock(?) THEN 1 ELSE 0 END"
            : "SELECT RELEASE_LOCK(?)", lockName);
        log.info("[workflow] released single-master runtime lock={}", lockName);
      }
    } catch (SQLException exception) {
      log.warn("[workflow] release single-master lock failed lock={}, message={}",
          lockName, exception.getMessage());
      try { connection.abort(Runnable::run); } catch (SQLException abortFailure) {
        exception.addSuppressed(abortFailure);
      }
    } finally {
      closeConnection();
    }
  }

  private String databaseName(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(postgresql
        ? "SELECT current_database() || '/' || current_schema()" : "SELECT DATABASE()");
         ResultSet result = statement.executeQuery()) {
      if (!result.next()) {
        throw new SQLException("Unable to resolve current database");
      }
      String database = result.getString(1);
      return database == null || database.isBlank() ? "default" : database;
    }
  }

  private Integer queryInteger(Connection connection, String sql, String value) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      if (postgresql) statement.setLong(1, advisoryKey);
      else statement.setString(1, value);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() ? result.getObject(1, Integer.class) : null;
      }
    }
  }

  private String lockName(String database) {
    return "yak-ops-workflow-" + HexFormat.of().formatHex(sha256(database), 0, 12);
  }

  private byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private void closeConnection() {
    Connection connection = ownershipConnection;
    ownershipConnection = null;
    if (connection == null) return;
    try {
      connection.close();
    } catch (SQLException ignored) {
      // Best effort during startup failure or shutdown.
    }
  }
}
