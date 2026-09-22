package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.ExecutionRecord;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.Page;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.PendingExecution;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository.Query;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** JDBC adapter for {@link DevelopmentTaskExecutionRepository}. */
@Repository
public class DevelopmentTaskExecutionRepositoryAdapter
    implements DevelopmentTaskExecutionRepository {

  private final JdbcTemplate jdbcTemplate;
  private final CurrentProject currentProject;

  @Autowired
  public DevelopmentTaskExecutionRepositoryAdapter(
      JdbcTemplate jdbcTemplate,
      CurrentProject currentProject) {
    this.jdbcTemplate = jdbcTemplate;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; project-scoped operations will fail closed. */
  DevelopmentTaskExecutionRepositoryAdapter(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public long createPending(PendingExecution pending) {
    Long projectId = requireOwnerProject(pending.projectId());
    String sql = "INSERT INTO yak_dev_task_execution "
        + "(project_id, node_id, task_name, task_type, schema_version, trigger_type, status, operator_name, "
        + "retry_of_execution_id, content, config_json, start_time, create_time, update_time) "
        + "VALUES (?, ?, ?, ?, ?, 'MANUAL', 'PENDING', ?, ?, ?, ?, NOW(6), NOW(6), NOW(6))";
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update(
        connection -> {
          PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          statement.setLong(1, projectId);
          statement.setLong(2, pending.nodeId());
          statement.setString(3, pending.taskName());
          statement.setString(4, pending.taskType());
          statement.setInt(5, pending.schemaVersion());
          statement.setString(6, pending.operatorName());
          if (pending.retryOfExecutionId() == null) statement.setObject(7, null);
          else statement.setLong(7, pending.retryOfExecutionId());
          statement.setString(8, pending.content());
          statement.setString(9, pending.configJson());
          return statement;
        },
        keyHolder);
    Number key = keyHolder.getKey();
    if (key == null) throw new IllegalStateException("创建运行记录失败：未返回主键");
    return key.longValue();
  }

  @Override
  public void attachRuntime(long id, String runtimeExecutionId, String status) {
    Long projectId = requiredProjectId();
    jdbcTemplate.update(
        "UPDATE yak_dev_task_execution SET runtime_execution_id = ?, status = ?, update_time = NOW(6) "
            + "WHERE id = ? AND project_id = ? AND status IN ('PENDING', 'RUNNING')",
        runtimeExecutionId,
        status,
        id,
        projectId);
  }

  @Override
  public void updateActiveStatus(long id, String status) {
    Long projectId = requiredProjectId();
    jdbcTemplate.update(
        "UPDATE yak_dev_task_execution SET status = ?, update_time = NOW(6) "
            + "WHERE id = ? AND project_id = ? AND status IN ('PENDING', 'RUNNING')",
        status,
        id,
        projectId);
  }

  @Override
  public void complete(
      long id,
      String status,
      long durationMs,
      String errorMessage,
      String outputJson) {
    Long projectId = requiredProjectId();
    jdbcTemplate.update(
        "UPDATE yak_dev_task_execution SET status = ?, duration_ms = ?, error_message = ?, output_json = ?, "
            + "end_time = NOW(6), update_time = NOW(6) WHERE id = ? AND project_id = ? "
            + "AND status IN ('PENDING', 'RUNNING')",
        status,
        durationMs,
        errorMessage,
        outputJson,
        id,
        projectId);
  }

  @Override
  public Page page(Query query) {
    List<Object> args = new ArrayList<>();
    String where = buildWhere(query, args);
    Long total = jdbcTemplate.queryForObject(
        "SELECT COUNT(1) FROM yak_dev_task_execution" + where,
        Long.class,
        args.toArray());

    List<Object> rowArgs = new ArrayList<>(args);
    rowArgs.add(query.pageSize());
    rowArgs.add((query.pageNo() - 1) * query.pageSize());
    List<ExecutionRecord> records = jdbcTemplate.query(
        detailSelect()
            + where
            + " ORDER BY start_time DESC, id DESC LIMIT ? OFFSET ?",
        DevelopmentTaskExecutionRepositoryAdapter::mapRecord,
        rowArgs.toArray());
    return new Page(records, total == null ? 0L : total, query.pageNo(), query.pageSize());
  }

  @Override
  public Optional<ExecutionRecord> findById(long id) {
    Long projectId = requiredProjectId();
    String sql = detailSelect() + " WHERE id = ? AND project_id = ? LIMIT 1";
    return jdbcTemplate.query(
            sql,
            DevelopmentTaskExecutionRepositoryAdapter::mapRecord,
            id,
            projectId)
        .stream()
        .findFirst();
  }

  @Override
  public Optional<ExecutionRecord> findLatestActiveByNode(long nodeId) {
    Long projectId = requiredProjectId();
    String sql = detailSelect()
        + " WHERE node_id = ? AND project_id = ? AND status IN ('PENDING', 'RUNNING')"
        + " ORDER BY start_time DESC, id DESC LIMIT 1";
    return jdbcTemplate.query(
            sql,
            DevelopmentTaskExecutionRepositoryAdapter::mapRecord,
            nodeId,
            projectId)
        .stream()
        .findFirst();
  }

  /** Explicit cross-project dispatcher corridor. Callers must restore each returned project before IO. */
  @Override
  public List<ExecutionRecord> listActiveForReconciliation(int limit) {
    int safeLimit = Math.max(1, Math.min(500, limit));
    return jdbcTemplate.query(
        detailSelect()
            + " WHERE project_id IS NOT NULL AND status IN ('PENDING', 'RUNNING')"
            + " ORDER BY update_time ASC, id ASC LIMIT ?",
        DevelopmentTaskExecutionRepositoryAdapter::mapRecord,
        safeLimit);
  }

  private String buildWhere(Query query, List<Object> args) {
    StringBuilder where = new StringBuilder(" WHERE project_id = ?");
    args.add(requiredProjectId());
    if (query.keyword() != null && !query.keyword().isBlank()) {
      where.append(" AND (task_name LIKE ? OR runtime_execution_id LIKE ? OR operator_name LIKE ?)");
      String like = "%" + query.keyword() + "%";
      args.add(like);
      args.add(like);
      args.add(like);
    }
    appendEquals(where, args, "status", query.status());
    appendEquals(where, args, "task_type", query.taskType());
    appendEquals(where, args, "trigger_type", query.triggerType());
    if (query.startTime() != null) {
      where.append(" AND start_time >= ?");
      args.add(Timestamp.valueOf(query.startTime()));
    }
    if (query.endTime() != null) {
      where.append(" AND start_time <= ?");
      args.add(Timestamp.valueOf(query.endTime()));
    }
    return where.toString();
  }

  private void appendEquals(StringBuilder where, List<Object> args, String column, String value) {
    if (value == null || value.isBlank()) return;
    where.append(" AND ").append(column).append(" = ?");
    args.add(value);
  }

  private static String detailSelect() {
    return "SELECT project_id, id, node_id, task_name, task_type, schema_version, trigger_type, "
        + "runtime_execution_id, retry_of_execution_id, status, operator_name, duration_ms, error_message, "
        + "content, config_json, output_json, start_time, end_time FROM yak_dev_task_execution";
  }

  private static ExecutionRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
    Object projectValue = rs.getObject("project_id");
    Long projectId = projectValue == null ? null : rs.getLong("project_id");
    long retry = rs.getLong("retry_of_execution_id");
    Long retryId = rs.wasNull() ? null : retry;
    long duration = rs.getLong("duration_ms");
    Long durationMs = rs.wasNull() ? null : duration;
    return new ExecutionRecord(
        projectId,
        rs.getLong("id"),
        rs.getLong("node_id"),
        rs.getString("task_name"),
        rs.getString("task_type"),
        rs.getInt("schema_version"),
        rs.getString("trigger_type"),
        rs.getString("runtime_execution_id"),
        retryId,
        rs.getString("status"),
        rs.getString("operator_name"),
        durationMs,
        rs.getString("error_message"),
        rs.getString("content"),
        rs.getString("config_json"),
        rs.getString("output_json"),
        toLocalDateTime(rs.getTimestamp("start_time")),
        toLocalDateTime(rs.getTimestamp("end_time")));
  }

  private Long requiredProjectId() {
    return currentProject.requireProjectId();
  }

  private Long requireOwnerProject(Long ownerProjectId) {
    Long currentProjectId = requiredProjectId();
    if (ownerProjectId == null
        || ownerProjectId <= 0L
        || !Objects.equals(currentProjectId, ownerProjectId)) {
      throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
    }
    return currentProjectId;
  }

  private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toLocalDateTime();
  }
}
