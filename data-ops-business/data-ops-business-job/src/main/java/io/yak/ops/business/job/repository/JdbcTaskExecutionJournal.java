package io.yak.ops.business.job.repository;

import io.yak.framework.common.jdbc.JdbcDatabase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.core.project.CurrentProject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Terminal results survive handle eviction; a completed idempotency key never starts new work. */
@Repository
@DependsOn("yakJobRuntimeFlyway")
@ConditionalOnProperty(prefix = "yak.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JdbcTaskExecutionJournal implements TaskExecutionJournal {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final CurrentProject project;

  public JdbcTaskExecutionJournal(@Qualifier("yakBusinessDataSource") DataSource dataSource,
      ObjectMapper mapper, CurrentProject project) {
    this.jdbc = new JdbcTemplate(dataSource);
    this.mapper = mapper;
    this.project = project;
  }

  @Override
  public void save(String taskType, String key, TaskExecution execution) {
    if (!execution.terminal()) throw new IllegalArgumentException("Only terminal evidence can be retained");
    final String output;
    try { output = mapper.writeValueAsString(execution.output()); }
    catch (Exception failure) { throw new IllegalStateException("Cannot encode task output", failure); }
    jdbc.update((jdbc.getDataSource() != null && JdbcDatabase.isPostgresql(jdbc.getDataSource())) ? """
        INSERT INTO yak_job_execution_result
          (execution_id, project_id, task_type, idempotency_hash, status, error_message, output_json)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (project_id, task_type, idempotency_hash) DO UPDATE SET
          execution_id = yak_job_execution_result.execution_id
        """ : """
        INSERT INTO yak_job_execution_result
          (execution_id, project_id, task_type, idempotency_hash, status, error_message, output_json)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE execution_id = execution_id
        """, execution.executionId(), project.requireProjectId(), taskType, digest(key),
        execution.status(), execution.errorMessage(), output);
  }

  @Override
  public Optional<TaskExecution> findById(String executionId) {
    return read("execution_id = ?", executionId);
  }

  @Override
  public Optional<TaskExecution> findByKey(String taskType, String key) {
    return read("task_type = ? AND idempotency_hash = ?", taskType, digest(key));
  }

  private Optional<TaskExecution> read(String predicate, Object... parameters) {
    Object[] args = new Object[parameters.length + 1];
    args[0] = project.requireProjectId();
    System.arraycopy(parameters, 0, args, 1, parameters.length);
    return jdbc.query("SELECT execution_id, status, error_message, output_json FROM "
        + "yak_job_execution_result WHERE project_id = ? AND " + predicate, (rs, row) -> {
          try {
            Map<String, Object> output = mapper.readValue(rs.getString("output_json"), new TypeReference<>() {});
            return new TaskExecution(rs.getString("execution_id"), rs.getString("status"),
                rs.getString("error_message"), output);
          } catch (Exception failure) {
            throw new IllegalStateException("Cannot decode retained task output", failure);
          }
        }, args).stream().findFirst();
  }

  private static String digest(String key) {
    if (key == null) return null;
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }
}
