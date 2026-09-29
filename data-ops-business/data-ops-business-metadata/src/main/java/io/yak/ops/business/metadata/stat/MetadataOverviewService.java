package io.yak.ops.business.metadata.stat;

import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-scoped technical collection and catalog overview. */
@Service
@ConditionalOnMetadataPersistence
public class MetadataOverviewService {

  private static final int RECENT_RUN_LIMIT = 5;

  private static final String ENTITY_COUNTS_SQL = """
      SELECT t.type_name, t.display_name, t.status, t.collectible, COUNT(a.id) AS entity_count
      FROM yak_md_type_def t
      LEFT JOIN yak_metadata_asset a
        ON a.type_id = t.id
       AND a.project_id = :projectId
       AND a.gone_at IS NULL
      WHERE t.category = 'ENTITY'
      GROUP BY t.id, t.type_name, t.display_name, t.status, t.collectible
      ORDER BY CASE WHEN t.status = 'ACTIVE' THEN 0 ELSE 1 END, t.display_name, t.type_name
      """;

  private static final String JOB_COUNTS_SQL = """
      SELECT provider_type,
             COUNT(*) AS total_count,
             SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END) AS enabled_count,
             SUM(CASE WHEN dry_run_passed = 0 THEN 1 ELSE 0 END) AS dry_run_required_count
      FROM yak_md_collect_job
      WHERE project_id = :projectId
        AND deleted = 0
      GROUP BY provider_type
      ORDER BY provider_type
      """;

  private static final String RECENT_RUNS_SQL = """
      SELECT r.id, r.job_id, j.job_code, j.job_name, r.provider_type, r.trigger_type, r.dry_run,
             r.status, r.cnt_total, r.cnt_new, r.cnt_changed, r.cnt_unchanged, r.cnt_gone,
             r.cnt_partial_failed, r.started_at, r.finished_at, r.duration_ms
      FROM yak_md_collect_run r
      LEFT JOIN yak_md_collect_job j
        ON j.id = r.job_id
       AND j.project_id = r.project_id
      WHERE r.project_id = :projectId
      ORDER BY r.id DESC
      LIMIT 5
      """;

  private static final String OPEN_TASKS_SQL = """
      SELECT task_type, COUNT(*) AS open_count
      FROM yak_md_task
      WHERE project_id = :projectId
        AND resolved_at IS NULL
      GROUP BY task_type
      ORDER BY task_type
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final CurrentProject currentProject;

  public MetadataOverviewService(
      @Qualifier("yakBusinessDataSource") DataSource dataSource,
      CurrentProject currentProject) {
    this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    this.currentProject = currentProject;
  }

  /** Runs four project-scoped queries; run history is capped at the latest five rows. */
  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Overview overview() {
    Map<String, Object> parameters = Map.of("projectId", currentProject.requireProjectId());
    List<EntityTypeCount> entityTypes =
        jdbc.query(
            ENTITY_COUNTS_SQL,
            parameters,
            (row, rowNumber) ->
                new EntityTypeCount(
                    row.getString("type_name"),
                    row.getString("display_name"),
                    row.getString("status"),
                    row.getBoolean("collectible"),
                    row.getLong("entity_count")));
    List<CollectionJobSummary> jobsByProvider =
        jdbc.query(
            JOB_COUNTS_SQL,
            parameters,
            (row, rowNumber) ->
                new CollectionJobSummary(
                    row.getString("provider_type"),
                    row.getLong("total_count"),
                    row.getLong("enabled_count"),
                    row.getLong("dry_run_required_count")));
    List<RecentRun> recentRuns =
        jdbc.query(
            RECENT_RUNS_SQL,
            parameters,
            (row, rowNumber) ->
                new RecentRun(
                    row.getLong("id"),
                    row.getLong("job_id"),
                    row.getString("job_code"),
                    row.getString("job_name"),
                    row.getString("provider_type"),
                    row.getString("trigger_type"),
                    row.getBoolean("dry_run"),
                    row.getString("status"),
                    row.getLong("cnt_total"),
                    row.getLong("cnt_new"),
                    row.getLong("cnt_changed"),
                    row.getLong("cnt_unchanged"),
                    row.getLong("cnt_gone"),
                    row.getLong("cnt_partial_failed"),
                    row.getObject("started_at", LocalDateTime.class),
                    row.getObject("finished_at", LocalDateTime.class),
                    row.getObject("duration_ms", Long.class)));
    List<OpenTaskSummary> openTasks =
        jdbc.query(
            OPEN_TASKS_SQL,
            parameters,
            (row, rowNumber) ->
                new OpenTaskSummary(row.getString("task_type"), row.getLong("open_count")));

    long catalogEntityCount = entityTypes.stream().mapToLong(EntityTypeCount::entityCount).sum();
    long totalJobCount = jobsByProvider.stream().mapToLong(CollectionJobSummary::totalCount).sum();
    long enabledJobCount = jobsByProvider.stream().mapToLong(CollectionJobSummary::enabledCount).sum();
    long dryRunRequiredJobCount =
        jobsByProvider.stream().mapToLong(CollectionJobSummary::dryRunRequiredCount).sum();
    long openTaskCount = openTasks.stream().mapToLong(OpenTaskSummary::openCount).sum();

    return new Overview(
        catalogEntityCount,
        List.copyOf(entityTypes),
        totalJobCount,
        enabledJobCount,
        dryRunRequiredJobCount,
        List.copyOf(jobsByProvider),
        RECENT_RUN_LIMIT,
        List.copyOf(recentRuns),
        openTaskCount,
        List.copyOf(openTasks));
  }

  public record Overview(
      long catalogEntityCount,
      List<EntityTypeCount> entityTypes,
      long totalJobCount,
      long enabledJobCount,
      long dryRunRequiredJobCount,
      List<CollectionJobSummary> jobsByProvider,
      int recentRunLimit,
      List<RecentRun> recentRuns,
      long openTaskCount,
      List<OpenTaskSummary> openTasks) {
  }

  public record EntityTypeCount(
      String typeName,
      String displayName,
      String status,
      boolean collectible,
      long entityCount) {
  }

  public record CollectionJobSummary(
      String providerType,
      long totalCount,
      long enabledCount,
      long dryRunRequiredCount) {
  }

  public record RecentRun(
      long runId,
      long jobId,
      String jobCode,
      String jobName,
      String providerType,
      String triggerType,
      boolean dryRun,
      String status,
      long totalCount,
      long newCount,
      long changedCount,
      long unchangedCount,
      long goneCount,
      long partialFailedCount,
      LocalDateTime startedAt,
      LocalDateTime finishedAt,
      Long durationMs) {
  }

  public record OpenTaskSummary(String taskType, long openCount) {
  }
}
