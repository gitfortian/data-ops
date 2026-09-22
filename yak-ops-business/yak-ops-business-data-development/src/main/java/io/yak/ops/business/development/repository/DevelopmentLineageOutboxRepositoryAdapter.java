package io.yak.ops.business.development.repository;

import io.yak.ops.business.development.repository.DevelopmentLineageOutboxRepository.OutboxRecord;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC adapter for {@link DevelopmentLineageOutboxRepository}. */
@Repository
public class DevelopmentLineageOutboxRepositoryAdapter
    implements DevelopmentLineageOutboxRepository {

  private final JdbcTemplate jdbc;
  private final CurrentProject currentProject;

  @Autowired
  public DevelopmentLineageOutboxRepositoryAdapter(
      JdbcTemplate jdbc, CurrentProject currentProject) {
    this.jdbc = jdbc;
    this.currentProject = currentProject;
  }

  /** Compatibility constructor for focused tests; enqueue will fail closed without ProjectContext. */
  DevelopmentLineageOutboxRepositoryAdapter(JdbcTemplate jdbc) {
    this(jdbc, Optional::<ProjectContext>empty);
  }

  @Override
  public void enqueue(String taskId, long nodeId, long revisionId) {
    Long projectId = currentProject.requireProjectId();
    Long ownedNodeCount = jdbc.queryForObject(
        "SELECT COUNT(1) FROM yak_dev_node WHERE id=? AND project_id=?",
        Long.class,
        nodeId,
        projectId);
    if (ownedNodeCount == null || ownedNodeCount != 1L) {
      throw new IllegalStateException(
          "无法为当前 Project 的数据开发节点创建血缘 Outbox：nodeId=" + nodeId);
    }

    // INSERT IGNORE is intentionally idempotent: the same node/revision may already have an outbox row.
    jdbc.update(
        "INSERT IGNORE INTO yak_dev_lineage_outbox "
            + "(task_id,project_id,node_id,revision_id,status,attempts,next_attempt_time,create_time,update_time) "
            + "SELECT ?,project_id,id,?,'PENDING',0,NOW(6),NOW(6),NOW(6) FROM yak_dev_node "
            + "WHERE id=? AND project_id=?",
        taskId,
        revisionId,
        nodeId,
        projectId);
  }

  /** Cross-project dispatcher query; every record carries durable project ownership. */
  @Override
  public List<OutboxRecord> due(int limit) {
    return jdbc.query(
        "SELECT task_id,project_id,node_id,revision_id,attempts FROM yak_dev_lineage_outbox "
            + "WHERE project_id IS NOT NULL AND ((status IN ('PENDING','FAILED') AND next_attempt_time<=NOW(6)) "
            + "OR (status='RUNNING' AND update_time<DATE_SUB(NOW(6), INTERVAL 10 MINUTE))) "
            + "ORDER BY create_time LIMIT ?",
        (rs, row) ->
            new OutboxRecord(
                rs.getString("task_id"),
                rs.getLong("project_id"),
                rs.getLong("node_id"),
                rs.getLong("revision_id"),
                rs.getInt("attempts")),
        limit);
  }

  @Override
  public boolean claim(OutboxRecord record) {
    return jdbc.update(
        "UPDATE yak_dev_lineage_outbox SET status='RUNNING',attempts=attempts+1,"
            + "update_time=NOW(6) WHERE task_id=? AND project_id=? AND revision_id=? "
            + "AND (status IN ('PENDING','FAILED') "
            + "OR (status='RUNNING' AND update_time<DATE_SUB(NOW(6), INTERVAL 10 MINUTE)))",
        record.taskId(),
        record.projectId(),
        record.revisionId()) == 1;
  }

  @Override
  public void complete(OutboxRecord record) {
    jdbc.update(
        "UPDATE yak_dev_lineage_outbox SET status='SUCCEEDED',last_error=NULL,update_time=NOW(6) "
            + "WHERE task_id=? AND project_id=? AND revision_id=? AND status='RUNNING'",
        record.taskId(),
        record.projectId(),
        record.revisionId());
  }

  @Override
  public void fail(OutboxRecord record, String errorMessage, long delaySeconds) {
    jdbc.update(
        "UPDATE yak_dev_lineage_outbox SET status='FAILED',last_error=?,"
            + "next_attempt_time=DATE_ADD(NOW(6), INTERVAL ? SECOND),update_time=NOW(6) "
            + "WHERE task_id=? AND project_id=? AND revision_id=? AND status='RUNNING'",
        errorMessage,
        delaySeconds,
        record.taskId(),
        record.projectId(),
        record.revisionId());
  }
}
