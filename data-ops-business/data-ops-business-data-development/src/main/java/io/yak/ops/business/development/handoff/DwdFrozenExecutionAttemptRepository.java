package io.yak.ops.business.development.handoff;

import io.yak.ops.core.project.CurrentProject;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Every lookup/update is project-scoped. The RESERVED -> DISPATCHING transition
 * is a durable CAS. UNKNOWN/acknowledged attempts are NEVER eligible for replay.
 */
@Repository
public class DwdFrozenExecutionAttemptRepository {
  private static final String FIELDS = "id,project_id,binding_id,binding_digest,"
      + "request_key,attempt_digest,state,runtime_execution_id,runtime_status";
  private final JdbcTemplate jdbc;
  private final CurrentProject projects;

  public DwdFrozenExecutionAttemptRepository(JdbcTemplate jdbc, CurrentProject projects) {
    this.jdbc = jdbc;
    this.projects = projects;
  }

  public void lockBinding(long bindingId) {
    var matched = jdbc.queryForList(
        "SELECT id FROM yak_dev_model_task_binding WHERE project_id=? AND id=? FOR UPDATE",
        Long.class, projects.requireProjectId(), bindingId);
    if (matched.size() != 1) {
      throw new IllegalArgumentException("当前项目不存在可用的冻结 DWD 绑定");
    }
  }

  public Optional<DwdFrozenExecutionAttempt> byKey(String requestKey) {
    return jdbc.query("SELECT " + FIELDS + " FROM yak_dev_dwd_execution_attempt "
            + "WHERE project_id=? AND request_key=? LIMIT 1",
        DwdFrozenExecutionAttemptRepository::map, projects.requireProjectId(), requestKey)
        .stream().findFirst();
  }

  public Optional<DwdFrozenExecutionAttempt> byId(long id) {
    return jdbc.query("SELECT " + FIELDS + " FROM yak_dev_dwd_execution_attempt "
            + "WHERE project_id=? AND id=? LIMIT 1",
        DwdFrozenExecutionAttemptRepository::map, projects.requireProjectId(), id)
        .stream().findFirst();
  }

  public DwdFrozenExecutionAttempt insert(DwdFrozenExecutionAttempt request) {
    if (request.id() != null || request.projectId() != projects.requireProjectId()
        || !"RESERVED".equals(request.state())) {
      throw new IllegalArgumentException("执行请求身份或初始状态非法");
    }
    var key = new GeneratedKeyHolder();
    jdbc.update(connection -> {
      var ps = connection.prepareStatement(
          "INSERT INTO yak_dev_dwd_execution_attempt "
          + "(project_id,binding_id,binding_digest,request_key,attempt_digest,state,"
          + "created_at,updated_at) VALUES (?,?,?,?,?,'RESERVED',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
          new String[] {"id"});
      ps.setLong(1, request.projectId());
      ps.setLong(2, request.bindingId());
      ps.setString(3, request.bindingDigest());
      ps.setString(4, request.requestKey());
      ps.setString(5, request.attemptDigest());
      return ps;
    }, key);
    if (key.getKey() == null) throw new IllegalStateException("执行请求没有数据库持久主键");
    return byId(key.getKey().longValue()).orElseThrow(
        () -> new IllegalStateException("持久执行请求回读失败"));
  }

  public boolean markDispatching(long attemptId) {
    return jdbc.update("UPDATE yak_dev_dwd_execution_attempt "
        + "SET state='DISPATCHING',updated_at=CURRENT_TIMESTAMP(6) "
        + "WHERE project_id=? AND id=? AND state='RESERVED'",
        projects.requireProjectId(), attemptId) == 1;
  }

  public void acknowledge(long attemptId, String runtimeId, String runtimeStatus) {
    if (runtimeId == null || runtimeId.isBlank() || runtimeId.length() > 256
        || runtimeStatus == null || runtimeStatus.isBlank() || runtimeStatus.length() > 32) {
      throw new IllegalArgumentException("Runtime 回执不完整");
    }
    int affected = jdbc.update("UPDATE yak_dev_dwd_execution_attempt "
        + "SET state='RUNTIME_ACKNOWLEDGED',runtime_execution_id=?,runtime_status=?,"
        + "updated_at=CURRENT_TIMESTAMP(6) "
        + "WHERE project_id=? AND id=? AND state='DISPATCHING'",
        runtimeId, runtimeStatus, projects.requireProjectId(), attemptId);
    if (affected != 1) throw new IllegalStateException("执行回执状态冲突，不能覆盖旧事实");
  }

  public void markUnknown(long attemptId) {
    // Never erase an already attached Runtime ID or allow UNKNOWN -> RESERVED.
    jdbc.update("UPDATE yak_dev_dwd_execution_attempt "
        + "SET state='UNKNOWN',updated_at=CURRENT_TIMESTAMP(6) "
        + "WHERE project_id=? AND id=? AND state='DISPATCHING'",
        projects.requireProjectId(), attemptId);
  }

  private static DwdFrozenExecutionAttempt map(ResultSet rs, int ignored) throws SQLException {
    return new DwdFrozenExecutionAttempt(
        rs.getLong("id"), rs.getLong("project_id"), rs.getLong("binding_id"),
        rs.getString("binding_digest"), rs.getString("request_key"),
        rs.getString("attempt_digest"), rs.getString("state"),
        rs.getString("runtime_execution_id"), rs.getString("runtime_status"));
  }
}
