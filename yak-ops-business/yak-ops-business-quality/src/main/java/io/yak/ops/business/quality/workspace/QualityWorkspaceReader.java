package io.yak.ops.business.quality.workspace;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.ColumnReport;
import io.yak.ops.business.quality.domain.QualityDomain.DimensionReport;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.OperationLog;
import io.yak.ops.business.quality.domain.QualityDomain.ReportOverview;
import io.yak.ops.business.quality.domain.QualityDomain.TrendPoint;
import io.yak.ops.business.quality.domain.QualityDomain.WorkspaceStats;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.quality.repository.QualityWorkspaceRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side monitor workspace/report projection. */
@Component
@ConditionalOnQualityEnabled
public class QualityWorkspaceReader {
  private final QualityMonitorReader monitorReader;
  private final QualityWorkspaceRepository repository;

  public QualityWorkspaceReader(
      QualityMonitorReader monitorReader,
      QualityWorkspaceRepository repository) {
    this.monitorReader = monitorReader;
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public MonitorWorkspace workspace(long monitorId) {
    Monitor monitor = monitorReader.require(monitorId);
    MonitorSettings settings = monitorReader.settings(monitorId);
    WorkspaceStats stats = repository.stats(monitorId);
    return new MonitorWorkspace(monitor, settings, stats);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public MonitorReport report(long monitorId, LocalDate reportDate) {
    monitorReader.require(monitorId);
    LocalDate normalized = reportDate == null ? LocalDate.now().minusDays(1) : reportDate;
    LocalDate trendStart = normalized.minusDays(6);
    LocalDateTime start = normalized.atStartOfDay();
    LocalDateTime end = normalized.plusDays(1).atStartOfDay();
    return new MonitorReport(
        normalized,
        trendStart,
        repository.overview(monitorId, start, end),
        repository.dimensions(monitorId, start, end),
        repository.trend(monitorId, trendStart.atStartOfDay(), end),
        repository.columns(monitorId, start, end));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public OperationLogPage operationLogs(long monitorId, Integer current, Integer pageSize) {
    monitorReader.require(monitorId);
    int page = current == null || current < 1 ? 1 : current;
    int size = pageSize == null ? 10 : Math.min(Math.max(pageSize, 1), 100);
    return new OperationLogPage(
        repository.operationLogs(monitorId, page, size),
        repository.countOperationLogs(monitorId),
        page,
        size);
  }

  public record MonitorWorkspace(
      Monitor monitor,
      MonitorSettings settings,
      WorkspaceStats stats) {}

  public record MonitorReport(
      LocalDate reportDate,
      LocalDate trendStart,
      ReportOverview overview,
      List<DimensionReport> dimensions,
      List<TrendPoint> trend,
      List<ColumnReport> columns) {
    public MonitorReport {
      dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
      trend = trend == null ? List.of() : List.copyOf(trend);
      columns = columns == null ? List.of() : List.copyOf(columns);
    }
  }

  public record OperationLogPage(
      List<OperationLog> records,
      long total,
      int current,
      int pageSize) {
    public OperationLogPage {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }
}
