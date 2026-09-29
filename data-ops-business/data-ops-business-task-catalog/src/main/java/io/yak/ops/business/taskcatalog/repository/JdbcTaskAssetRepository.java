package io.yak.ops.business.taskcatalog.repository;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContextError;
import io.yak.ops.core.project.ProjectContextException;
import io.yak.ops.spi.task.model.TaskAssetSource;
import io.yak.ops.spi.task.model.TaskAssetStatus;
import io.yak.ops.spi.task.model.TaskRevisionRef;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC storage for the small, source-neutral task asset index. */
@Repository
@ConditionalOnDataSourceEnabled
public class JdbcTaskAssetRepository implements TaskAssetRepository {

  private static final String SELECT_COLUMNS =
      "SELECT id, source, source_ref, project_id, name, task_type, status, "
          + "current_revision_id, current_revision_no, create_time, update_time "
          + "FROM yak_task_asset";

  private final JdbcTemplate jdbcTemplate;
  private final CurrentProject currentProject;

  @Autowired
  public JdbcTaskAssetRepository(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      CurrentProject currentProject) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.currentProject = currentProject;
  }

  public JdbcTaskAssetRepository(DataSource dataSource) {
    this(dataSource, Optional::<io.yak.ops.core.project.ProjectContext>empty);
  }

  @Override
  public TaskAsset upsertPublished(
      TaskAssetSource source,
      String sourceRef,
      Long projectId,
      String name,
      String taskType,
      long revisionId,
      int revisionNo) {
    Long effectiveProjectId = effectiveProjectId(projectId);
    claimLegacyProject(source, sourceRef, effectiveProjectId);
    jdbcTemplate.update(
        """
        INSERT INTO yak_task_asset (
            source, source_ref, project_id, name, task_type, status,
            current_revision_id, current_revision_no, create_time, update_time)
        VALUES (?, ?, ?, ?, ?, 'ONLINE', ?, ?, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            project_id = VALUES(project_id),
            name = VALUES(name),
            task_type = VALUES(task_type),
            status = 'ONLINE',
            current_revision_id = VALUES(current_revision_id),
            current_revision_no = VALUES(current_revision_no),
            update_time = NOW(6)
        """,
        source.name(),
        sourceRef,
        effectiveProjectId,
        name,
        taskType,
        revisionId,
        revisionNo);
    return findBySourceInProject(source, sourceRef, effectiveProjectId)
        .orElseThrow(() -> new IllegalStateException("任务资产发布后无法重新读取：" + source + "/" + sourceRef));
  }

  @Override
  public Optional<TaskAsset> findById(long assetId) {
    Long projectId = currentProjectId();
    String sql = SELECT_COLUMNS + " WHERE id = ?"
        + (projectId == null ? "" : " AND project_id = ?")
        + " LIMIT 1";
    Object[] args = projectId == null ? new Object[] {assetId} : new Object[] {assetId, projectId};
    return jdbcTemplate.query(sql, JdbcTaskAssetRepository::mapRow, args).stream().findFirst();
  }

  @Override
  public Optional<TaskAsset> findBySource(TaskAssetSource source, String sourceRef) {
    Long projectId = currentProjectId();
    return findBySourceInProject(source, sourceRef, projectId);
  }

  @Override
  public List<TaskAsset> list(
      TaskAssetSource source,
      TaskAssetStatus status,
      String keyword) {
    StringBuilder sql = new StringBuilder(SELECT_COLUMNS).append(" WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    Long projectId = currentProjectId();
    if (projectId != null) {
      sql.append(" AND project_id = ?");
      args.add(projectId);
    }
    if (source != null) {
      sql.append(" AND source = ?");
      args.add(source.name());
    }
    if (status != null) {
      sql.append(" AND status = ?");
      args.add(status.name());
    }
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND (name LIKE ? OR source_ref LIKE ?)");
      String pattern = "%" + keyword.trim() + "%";
      args.add(pattern);
      args.add(pattern);
    }
    sql.append(" ORDER BY update_time DESC, id DESC");
    return jdbcTemplate.query(sql.toString(), JdbcTaskAssetRepository::mapRow, args.toArray());
  }

  @Override
  public boolean updateSourceMetadata(
      TaskAssetSource source,
      String sourceRef,
      Long projectId,
      String name,
      String taskType) {
    Long effectiveProjectId = effectiveProjectId(projectId);
    claimLegacyProject(source, sourceRef, effectiveProjectId);
    String sql = "UPDATE yak_task_asset SET project_id = ?, name = ?, task_type = ?, update_time = NOW(6) "
        + "WHERE source = ? AND source_ref = ?"
        + (effectiveProjectId == null ? " AND project_id IS NULL" : " AND project_id = ?");
    List<Object> args = new ArrayList<>();
    args.add(effectiveProjectId);
    args.add(name);
    args.add(taskType);
    args.add(source.name());
    args.add(sourceRef);
    if (effectiveProjectId != null) args.add(effectiveProjectId);
    return jdbcTemplate.update(sql, args.toArray()) > 0;
  }

  @Override
  public boolean updateStatus(
      TaskAssetSource source,
      String sourceRef,
      TaskAssetStatus status) {
    Long projectId = currentProjectId();
    String sql = "UPDATE yak_task_asset SET status = ?, update_time = NOW(6) WHERE source = ? AND source_ref = ?"
        + (projectId == null ? " AND project_id IS NULL" : " AND project_id = ?");
    Object[] args = projectId == null
        ? new Object[] {status.name(), source.name(), sourceRef}
        : new Object[] {status.name(), source.name(), sourceRef, projectId};
    return jdbcTemplate.update(sql, args) > 0;
  }

  private Optional<TaskAsset> findBySourceInProject(
      TaskAssetSource source, String sourceRef, Long projectId) {
    String sql = SELECT_COLUMNS + " WHERE source = ? AND source_ref = ?"
        + (projectId == null ? " AND project_id IS NULL" : " AND project_id = ?")
        + " LIMIT 1";
    Object[] args = projectId == null
        ? new Object[] {source.name(), sourceRef}
        : new Object[] {source.name(), sourceRef, projectId};
    return jdbcTemplate.query(sql, JdbcTaskAssetRepository::mapRow, args).stream().findFirst();
  }

  private void claimLegacyProject(
      TaskAssetSource source, String sourceRef, Long projectId) {
    if (projectId == null) return;
    jdbcTemplate.update(
        """
        UPDATE yak_task_asset legacy
        LEFT JOIN yak_task_asset scoped
          ON scoped.source = legacy.source
         AND scoped.source_ref = legacy.source_ref
         AND scoped.project_id = ?
        SET legacy.project_id = ?, legacy.update_time = NOW(6)
        WHERE legacy.source = ?
          AND legacy.source_ref = ?
          AND legacy.project_id IS NULL
          AND scoped.id IS NULL
        """,
        projectId,
        projectId,
        source.name(),
        sourceRef);
  }

  private Long currentProjectId() {
    return currentProject.current().map(context -> context.projectId()).orElse(null);
  }

  private Long effectiveProjectId(Long sourceProjectId) {
    return currentProject.current()
        .map(
            context -> {
              if (sourceProjectId != null && !Objects.equals(context.projectId(), sourceProjectId)) {
                throw new ProjectContextException(ProjectContextError.PROJECT_NOT_FOUND);
              }
              return context.projectId();
            })
        .orElse(sourceProjectId);
  }

  private static TaskAsset mapRow(ResultSet resultSet, int rowNum) throws SQLException {
    long id = resultSet.getLong("id");
    Object projectValue = resultSet.getObject("project_id");
    Long projectId = projectValue == null ? null : resultSet.getLong("project_id");
    long revisionId = resultSet.getLong("current_revision_id");
    int revisionNo = resultSet.getInt("current_revision_no");
    return new TaskAsset(
        id,
        TaskAssetSource.valueOf(resultSet.getString("source")),
        resultSet.getString("source_ref"),
        projectId,
        resultSet.getString("name"),
        resultSet.getString("task_type"),
        TaskAssetStatus.valueOf(resultSet.getString("status")),
        new TaskRevisionRef(id, revisionId, revisionNo),
        instant(resultSet.getTimestamp("create_time")),
        instant(resultSet.getTimestamp("update_time")));
  }

  private static Instant instant(Timestamp value) {
    return value == null ? null : value.toInstant();
  }
}
