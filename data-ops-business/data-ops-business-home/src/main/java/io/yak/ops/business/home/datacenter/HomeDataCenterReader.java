package io.yak.ops.business.home.datacenter;

import io.yak.ops.business.quality.workspace.QualityExecutionOverviewReader;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionOverviewReader;
import io.yak.ops.business.workflow.execution.WorkflowExecutionOverviewReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 首页数据中心只读聚合；领域运行语义由各业务 Reader 自己拥有。 */
@Component
public class HomeDataCenterReader {

  private static final Logger LOG = LoggerFactory.getLogger(HomeDataCenterReader.class);
  private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
  private static final int RECENT_TASK_LIMIT = 5;
  private static final int SCHEDULE_SOURCE_LIMIT = 20;

  private final ObjectProvider<OfflineExecutionOverviewReader> offlineReaderProvider;
  private final ObjectProvider<WorkflowExecutionOverviewReader> workflowReaderProvider;
  private final ObjectProvider<QualityExecutionOverviewReader> qualityReaderProvider;

  public HomeDataCenterReader(
      ObjectProvider<OfflineExecutionOverviewReader> offlineReaderProvider,
      ObjectProvider<WorkflowExecutionOverviewReader> workflowReaderProvider,
      ObjectProvider<QualityExecutionOverviewReader> qualityReaderProvider) {
    this.offlineReaderProvider = offlineReaderProvider;
    this.workflowReaderProvider = workflowReaderProvider;
    this.qualityReaderProvider = qualityReaderProvider;
  }

  public OverviewResponse overview(String periodValue) {
    PeriodRange range = PeriodRange.resolve(periodValue);
    PeriodRange previous = range.previous();
    List<SourceOverview> current = sourceOverviews(range);
    List<SourceOverview> previousSnapshots = sourceOverviews(previous);

    Metrics currentMetrics = mergeMetrics(current);
    Metrics previousMetrics = mergeMetrics(previousSnapshots);
    return new OverviewResponse(
        period(range),
        latestTask(current),
        mergeTrend(range, current),
        currentMetrics,
        compare(currentMetrics, previousMetrics));
  }

  public RecentResponse recent() {
    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    LocalDateTime start = end.minusDays(7);
    List<SourceTask> items = new ArrayList<>();
    items.addAll(offlineRecentTasks(start, end));
    items.addAll(workflowRecentTasks(start, end));
    items.addAll(qualityRecentTasks(start, end));
    items.sort(Comparator.comparing(SourceTask::lastRunTime).reversed());

    return new RecentResponse(items.stream()
        .limit(RECENT_TASK_LIMIT)
        .map(this::recentTask)
        .toList());
  }

  public ScheduleResponse schedules(String periodValue) {
    PeriodRange range = PeriodRange.resolve(periodValue);
    List<SourceSchedule> items = new ArrayList<>();
    items.addAll(offlineSchedules());
    items.addAll(workflowSchedules());
    items.sort(Comparator.comparing(
        SourceSchedule::nextScheduleTime,
        Comparator.nullsLast(Comparator.naturalOrder())));

    List<ScheduleItem> result = items.stream().limit(8).map(this::scheduleItem).toList();
    return new ScheduleResponse(period(range), result.size(), result);
  }

  private List<SourceOverview> sourceOverviews(PeriodRange range) {
    boolean hourly = range.days() == 1;
    return List.of(
        offlineOverview(range.start(), range.end(), hourly),
        workflowOverview(range.start(), range.end(), hourly),
        qualityOverview(range.start(), range.end(), hourly));
  }

  private SourceOverview offlineOverview(
      LocalDateTime start, LocalDateTime end, boolean hourly) {
    OfflineExecutionOverviewReader reader = offlineReaderProvider.getIfAvailable();
    if (reader == null) return SourceOverview.empty("OFFLINE_SYNC");
    try {
      OfflineExecutionOverviewReader.Overview overview = reader.overview(start, end, hourly);
      return new SourceOverview(
          "OFFLINE_SYNC",
          sourceMetrics(overview.metrics()),
          overview.trend().stream()
              .map(item -> new SourceTrend(item.bucket(), item.count()))
              .toList(),
          offlineExecution(overview.latest()));
    } catch (RuntimeException exception) {
      LOG.warn("加载首页离线同步运行读模型失败", exception);
      return SourceOverview.empty("OFFLINE_SYNC");
    }
  }

  private SourceOverview workflowOverview(
      LocalDateTime start, LocalDateTime end, boolean hourly) {
    WorkflowExecutionOverviewReader reader = workflowReaderProvider.getIfAvailable();
    if (reader == null) return SourceOverview.empty("WORKFLOW");
    try {
      WorkflowExecutionOverviewReader.Overview overview = reader.overview(start, end, hourly);
      return new SourceOverview(
          "WORKFLOW",
          sourceMetrics(overview.metrics()),
          overview.trend().stream()
              .map(item -> new SourceTrend(item.bucket(), item.count()))
              .toList(),
          workflowExecution(overview.latest()));
    } catch (RuntimeException exception) {
      LOG.warn("加载首页工作流运行读模型失败", exception);
      return SourceOverview.empty("WORKFLOW");
    }
  }

  private SourceOverview qualityOverview(
      LocalDateTime start, LocalDateTime end, boolean hourly) {
    QualityExecutionOverviewReader reader = qualityReaderProvider.getIfAvailable();
    if (reader == null) return SourceOverview.empty("DATA_QUALITY");
    try {
      QualityExecutionOverviewReader.Overview overview = reader.overview(start, end, hourly);
      return new SourceOverview(
          "DATA_QUALITY",
          sourceMetrics(overview.metrics()),
          overview.trend().stream()
              .map(item -> new SourceTrend(item.bucket(), item.count()))
              .toList(),
          qualityExecution(overview.latest()));
    } catch (RuntimeException exception) {
      LOG.warn("加载首页数据质量运行读模型失败", exception);
      return SourceOverview.empty("DATA_QUALITY");
    }
  }

  private SourceMetrics sourceMetrics(OfflineExecutionOverviewReader.Metrics metrics) {
    return new SourceMetrics(
        metrics.successCount(), metrics.runningCount(), metrics.failedCount(),
        metrics.scheduleCount(), metrics.processedRecords(), metrics.durationTotalMs(),
        metrics.durationSampleCount());
  }

  private SourceMetrics sourceMetrics(WorkflowExecutionOverviewReader.Metrics metrics) {
    return new SourceMetrics(
        metrics.successCount(), metrics.runningCount(), metrics.failedCount(),
        metrics.scheduleCount(), metrics.processedRecords(), metrics.durationTotalMs(),
        metrics.durationSampleCount());
  }

  private SourceMetrics sourceMetrics(QualityExecutionOverviewReader.Metrics metrics) {
    return new SourceMetrics(
        metrics.successCount(), metrics.runningCount(), metrics.failedCount(),
        metrics.scheduleCount(), metrics.processedRecords(), metrics.durationTotalMs(),
        metrics.durationSampleCount());
  }

  private Metrics mergeMetrics(List<SourceOverview> overviews) {
    long success = 0L;
    long running = 0L;
    long failed = 0L;
    long schedule = 0L;
    long processed = 0L;
    long durationTotal = 0L;
    long durationSamples = 0L;
    for (SourceOverview overview : overviews) {
      SourceMetrics metrics = overview.metrics();
      success += metrics.successCount();
      running += metrics.runningCount();
      failed += metrics.failedCount();
      schedule += metrics.scheduleCount();
      processed += metrics.processedRecords();
      durationTotal += metrics.durationTotalMs();
      durationSamples += metrics.durationSampleCount();
    }
    long avgDuration = durationSamples == 0L
        ? 0L
        : Math.round(durationTotal / (double) durationSamples);
    return new Metrics(success, running, failed, schedule, processed, avgDuration);
  }

  private Trend mergeTrend(PeriodRange range, List<SourceOverview> overviews) {
    Map<LocalDateTime, Long> counts = new LinkedHashMap<>();
    for (SourceOverview overview : overviews) {
      for (SourceTrend item : overview.trend()) {
        counts.merge(item.bucket(), item.count(), Long::sum);
      }
    }

    List<String> labels = new ArrayList<>();
    List<Long> values = new ArrayList<>();
    if (range.days() == 1) {
      for (int hour = 0; hour <= 24; hour += 4) {
        labels.add(String.format(Locale.ROOT, "%02d:00", hour));
        values.add(hour == 24
            ? 0L
            : counts.getOrDefault(range.start().toLocalDate().atTime(hour, 0), 0L));
      }
      return new Trend(labels, values);
    }

    for (int i = 0; i < range.days(); i++) {
      LocalDate day = range.start().toLocalDate().plusDays(i);
      labels.add(DAY_LABEL.format(day));
      values.add(counts.getOrDefault(day.atStartOfDay(), 0L));
    }
    return new Trend(labels, values);
  }

  private LatestTask latestTask(List<SourceOverview> current) {
    SourceExecution latest = current.stream()
        .map(SourceOverview::latest)
        .filter(item -> item != null && item.occurredAt() != null)
        .max(Comparator.comparing(SourceExecution::occurredAt))
        .orElseGet(this::latestExecutionAcrossAll);
    if (latest == null) return null;

    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    SourceTask activity = taskSummary(latest.taskType(), latest.taskId(), end.minusDays(7), end);
    return new LatestTask(
        latest.taskId(),
        latest.taskType(),
        latest.taskName(),
        latest.durationMs(),
        activity == null ? 0L : activity.runCount(),
        activity == null ? 0L : activity.failedCount(),
        latest.status(),
        detailPath(latest.taskType(), latest.taskId(), latest.executionId()));
  }

  private SourceExecution latestExecutionAcrossAll() {
    LocalDateTime end = LocalDateTime.now().plusNanos(1);
    LocalDateTime start = end.minusDays(90);
    List<SourceExecution> candidates = new ArrayList<>();

    OfflineExecutionOverviewReader offline = offlineReaderProvider.getIfAvailable();
    if (offline != null) {
      try {
        SourceExecution item = offlineExecution(offline.latest(start, end));
        if (item != null) candidates.add(item);
      } catch (RuntimeException exception) {
        LOG.warn("加载首页离线同步最近运行失败", exception);
      }
    }

    WorkflowExecutionOverviewReader workflow = workflowReaderProvider.getIfAvailable();
    if (workflow != null) {
      try {
        SourceExecution item = workflowExecution(workflow.latest(start, end));
        if (item != null) candidates.add(item);
      } catch (RuntimeException exception) {
        LOG.warn("加载首页工作流最近运行失败", exception);
      }
    }

    QualityExecutionOverviewReader quality = qualityReaderProvider.getIfAvailable();
    if (quality != null) {
      try {
        SourceExecution item = qualityExecution(quality.latest(start, end));
        if (item != null) candidates.add(item);
      } catch (RuntimeException exception) {
        LOG.warn("加载首页质量最近运行失败", exception);
      }
    }

    return candidates.stream()
        .filter(item -> item.occurredAt() != null)
        .max(Comparator.comparing(SourceExecution::occurredAt))
        .orElse(null);
  }

  private SourceTask taskSummary(
      String taskType, String taskId, LocalDateTime start, LocalDateTime end) {
    try {
      return switch (taskType) {
        case "OFFLINE_SYNC" -> {
          OfflineExecutionOverviewReader reader = offlineReaderProvider.getIfAvailable();
          yield reader == null ? null : offlineTask(reader.taskSummary(taskId, start, end));
        }
        case "WORKFLOW" -> {
          WorkflowExecutionOverviewReader reader = workflowReaderProvider.getIfAvailable();
          yield reader == null ? null : workflowTask(reader.taskSummary(taskId, start, end));
        }
        case "DATA_QUALITY" -> {
          QualityExecutionOverviewReader reader = qualityReaderProvider.getIfAvailable();
          yield reader == null ? null : qualityTask(reader.taskSummary(taskId, start, end));
        }
        default -> null;
      };
    } catch (RuntimeException exception) {
      LOG.warn("加载首页任务运行摘要失败: taskType={}, taskId={}", taskType, taskId, exception);
      return null;
    }
  }

  private List<SourceTask> offlineRecentTasks(LocalDateTime start, LocalDateTime end) {
    OfflineExecutionOverviewReader reader = offlineReaderProvider.getIfAvailable();
    if (reader == null) return List.of();
    try {
      return reader.recentTasks(start, end, RECENT_TASK_LIMIT).stream()
          .map(this::offlineTask)
          .toList();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页离线同步近期任务失败", exception);
      return List.of();
    }
  }

  private List<SourceTask> workflowRecentTasks(LocalDateTime start, LocalDateTime end) {
    WorkflowExecutionOverviewReader reader = workflowReaderProvider.getIfAvailable();
    if (reader == null) return List.of();
    try {
      return reader.recentTasks(start, end, RECENT_TASK_LIMIT).stream()
          .map(this::workflowTask)
          .toList();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页工作流近期任务失败", exception);
      return List.of();
    }
  }

  private List<SourceTask> qualityRecentTasks(LocalDateTime start, LocalDateTime end) {
    QualityExecutionOverviewReader reader = qualityReaderProvider.getIfAvailable();
    if (reader == null) return List.of();
    try {
      return reader.recentTasks(start, end, RECENT_TASK_LIMIT).stream()
          .map(this::qualityTask)
          .toList();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页质量近期任务失败", exception);
      return List.of();
    }
  }

  private List<SourceSchedule> offlineSchedules() {
    OfflineExecutionOverviewReader reader = offlineReaderProvider.getIfAvailable();
    if (reader == null) return List.of();
    try {
      return reader.schedules(SCHEDULE_SOURCE_LIMIT).stream()
          .map(item -> new SourceSchedule(
              "OFFLINE_SYNC",
              item.taskId(),
              item.taskName(),
              item.cronExpression(),
              item.status(),
              item.lastScheduleTime(),
              item.nextScheduleTime()))
          .toList();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页离线同步调度摘要失败", exception);
      return List.of();
    }
  }

  private List<SourceSchedule> workflowSchedules() {
    WorkflowExecutionOverviewReader reader = workflowReaderProvider.getIfAvailable();
    if (reader == null) return List.of();
    try {
      return reader.schedules(SCHEDULE_SOURCE_LIMIT).stream()
          .map(item -> new SourceSchedule(
              "WORKFLOW",
              item.taskId(),
              item.taskName(),
              item.cronExpression(),
              item.status(),
              item.lastScheduleTime(),
              item.nextScheduleTime()))
          .toList();
    } catch (RuntimeException exception) {
      LOG.warn("加载首页工作流调度摘要失败", exception);
      return List.of();
    }
  }

  private SourceExecution offlineExecution(OfflineExecutionOverviewReader.Execution item) {
    return item == null ? null : new SourceExecution(
        "OFFLINE_SYNC", item.taskId(), item.taskName(), item.status(),
        item.occurredAt(), item.durationMs(), item.executionId());
  }

  private SourceExecution workflowExecution(WorkflowExecutionOverviewReader.Execution item) {
    return item == null ? null : new SourceExecution(
        "WORKFLOW", item.taskId(), item.taskName(), item.status(),
        item.occurredAt(), item.durationMs(), item.executionId());
  }

  private SourceExecution qualityExecution(QualityExecutionOverviewReader.Execution item) {
    return item == null ? null : new SourceExecution(
        "DATA_QUALITY", item.taskId(), item.taskName(), item.status(),
        item.occurredAt(), item.durationMs(), item.executionId());
  }

  private SourceTask offlineTask(OfflineExecutionOverviewReader.TaskSummary item) {
    return item == null ? null : new SourceTask(
        "OFFLINE_SYNC", item.taskId(), item.taskName(), item.lastRunTime(), item.runCount(),
        item.successCount(), item.failedCount(), item.lastDurationMs(), item.lastStatus(),
        item.executionId());
  }

  private SourceTask workflowTask(WorkflowExecutionOverviewReader.TaskSummary item) {
    return item == null ? null : new SourceTask(
        "WORKFLOW", item.taskId(), item.taskName(), item.lastRunTime(), item.runCount(),
        item.successCount(), item.failedCount(), item.lastDurationMs(), item.lastStatus(),
        item.executionId());
  }

  private SourceTask qualityTask(QualityExecutionOverviewReader.TaskSummary item) {
    return item == null ? null : new SourceTask(
        "DATA_QUALITY", item.taskId(), item.taskName(), item.lastRunTime(), item.runCount(),
        item.successCount(), item.failedCount(), item.lastDurationMs(), item.lastStatus(),
        item.executionId());
  }

  private RecentTask recentTask(SourceTask item) {
    return new RecentTask(
        item.taskId(),
        item.taskType(),
        item.taskName(),
        item.lastRunTime().toString(),
        item.runCount(),
        item.successCount(),
        item.failedCount(),
        item.lastDurationMs(),
        item.lastStatus(),
        detailPath(item.taskType(), item.taskId(), item.executionId()));
  }

  private ScheduleItem scheduleItem(SourceSchedule item) {
    return new ScheduleItem(
        item.taskId(),
        item.taskType(),
        item.taskName(),
        item.cronExpression(),
        item.status(),
        text(item.lastScheduleTime()),
        text(item.nextScheduleTime()),
        detailPath(item.taskType(), item.taskId(), null));
  }

  private MetricCompare compare(Metrics current, Metrics previous) {
    return new MetricCompare(
        current.successCount() - previous.successCount(),
        current.runningCount() - previous.runningCount(),
        current.failedCount() - previous.failedCount(),
        current.scheduleCount() - previous.scheduleCount(),
        rateChange(current.processedRecords(), previous.processedRecords()),
        current.avgDurationMs() - previous.avgDurationMs());
  }

  private BigDecimal rateChange(long current, long previous) {
    if (previous <= 0L) return current <= 0L ? BigDecimal.ZERO : BigDecimal.valueOf(100);
    return BigDecimal.valueOf(current - previous)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
  }

  private String detailPath(String taskType, String taskId, String executionId) {
    return switch (taskType) {
      case "OFFLINE_SYNC" -> "/sync/batch-link-up/" + taskId + "/detail";
      case "WORKFLOW" -> executionId == null ? "/workflow/schedules" : "/workflow/instances";
      case "DATA_QUALITY" -> executionId == null
          ? "/data-quality/overview"
          : "/data-quality/execution/" + executionId;
      default -> "/home";
    };
  }

  private PeriodView period(PeriodRange range) {
    return new PeriodView(
        range.start().toLocalDate().toString(),
        range.end().minusNanos(1).toLocalDate().toString());
  }

  private static String text(LocalDateTime value) {
    return value == null ? null : value.toString();
  }

  public record OverviewResponse(
      PeriodView period,
      LatestTask latestTask,
      Trend trend,
      Metrics metrics,
      MetricCompare compare) {}

  public record PeriodView(String start, String end) {}

  public record Trend(List<String> labels, List<Long> values) {}

  public record Metrics(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      long processedRecords,
      long avgDurationMs) {}

  public record MetricCompare(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      BigDecimal processedRecordsRate,
      long avgDurationMs) {}

  public record LatestTask(
      String taskId,
      String taskType,
      String taskName,
      long durationMs,
      long runCount,
      long exceptionCount,
      String status,
      String detailPath) {}

  public record RecentResponse(List<RecentTask> items) {}

  public record RecentTask(
      String taskId,
      String taskType,
      String taskName,
      String lastRunTime,
      long runCount,
      long successCount,
      long failedCount,
      long lastDurationMs,
      String lastStatus,
      String detailPath) {}

  public record ScheduleResponse(PeriodView period, long total, List<ScheduleItem> items) {}

  public record ScheduleItem(
      String taskId,
      String taskType,
      String taskName,
      String cronExpression,
      String status,
      String lastScheduleTime,
      String nextScheduleTime,
      String detailPath) {}

  private record SourceOverview(
      String taskType,
      SourceMetrics metrics,
      List<SourceTrend> trend,
      SourceExecution latest) {
    static SourceOverview empty(String taskType) {
      return new SourceOverview(taskType, SourceMetrics.empty(), List.of(), null);
    }
  }

  private record SourceMetrics(
      long successCount,
      long runningCount,
      long failedCount,
      long scheduleCount,
      long processedRecords,
      long durationTotalMs,
      long durationSampleCount) {
    static SourceMetrics empty() {
      return new SourceMetrics(0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }
  }

  private record SourceTrend(LocalDateTime bucket, long count) {}

  private record SourceExecution(
      String taskType,
      String taskId,
      String taskName,
      String status,
      LocalDateTime occurredAt,
      long durationMs,
      String executionId) {}

  private record SourceTask(
      String taskType,
      String taskId,
      String taskName,
      LocalDateTime lastRunTime,
      long runCount,
      long successCount,
      long failedCount,
      long lastDurationMs,
      String lastStatus,
      String executionId) {}

  private record SourceSchedule(
      String taskType,
      String taskId,
      String taskName,
      String cronExpression,
      String status,
      LocalDateTime lastScheduleTime,
      LocalDateTime nextScheduleTime) {}

  private record PeriodRange(LocalDateTime start, LocalDateTime end, int days) {
    static PeriodRange resolve(String value) {
      String normalized = value == null ? "7d" : value.trim().toLowerCase(Locale.ROOT);
      int days = switch (normalized) {
        case "yesterday" -> 1;
        case "30d" -> 30;
        default -> 7;
      };
      LocalDate yesterday = LocalDate.now().minusDays(1);
      LocalDate startDay = yesterday.minusDays(days - 1L);
      return new PeriodRange(startDay.atStartOfDay(), yesterday.plusDays(1).atStartOfDay(), days);
    }

    PeriodRange previous() {
      return new PeriodRange(start.minusDays(days), start, days);
    }
  }
}
