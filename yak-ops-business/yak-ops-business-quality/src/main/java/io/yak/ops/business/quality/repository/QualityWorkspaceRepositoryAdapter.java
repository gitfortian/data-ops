package io.yak.ops.business.quality.repository;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityAnalyticsDao;
import io.yak.ops.business.quality.domain.QualityDomain.ColumnReport;
import io.yak.ops.business.quality.domain.QualityDomain.DimensionReport;
import io.yak.ops.business.quality.domain.QualityDomain.OperationLog;
import io.yak.ops.business.quality.domain.QualityDomain.ReportOverview;
import io.yak.ops.business.quality.domain.QualityDomain.TrendPoint;
import io.yak.ops.business.quality.domain.QualityDomain.WorkspaceStats;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityWorkspaceRepositoryAdapter implements QualityWorkspaceRepository {
  private final QualityAnalyticsDao analyticsDao;

  @Override
  public WorkspaceStats stats(long monitorId) {
    var row = analyticsDao.selectStats(monitorId);
    return new WorkspaceStats(nvl(row.getRuleCount()), nvl(row.getEnabledRuleCount()),
        nvl(row.getExecutionCount()), nvl(row.getIssueExecutionCount()), row.getLatestExecutionTime());
  }

  @Override
  public ReportOverview overview(long monitorId, LocalDateTime reportStart, LocalDateTime reportEnd) {
    var row = analyticsDao.selectOverview(reportParams(monitorId, reportStart, reportEnd));
    int executed = nvl(row.getExecutedRules());
    int passed = nvl(row.getPassedRules());
    return new ReportOverview(nvl(row.getTotalRules()), nvl(row.getEnabledRules()), executed,
        nvl(row.getIssueRules()), nvl(row.getErrorRules()), rate(passed, executed));
  }

  @Override
  public List<DimensionReport> dimensions(long monitorId, LocalDateTime reportStart, LocalDateTime reportEnd) {
    return analyticsDao.selectDimensions(reportParams(monitorId, reportStart, reportEnd)).stream()
        .map(row -> new DimensionReport(row.getDimension(), nvl(row.getTotalCount()), nvl(row.getPassedCount()),
            nvl(row.getNotPassedCount()), nvl(row.getErrorCount()), rate(nvl(row.getPassedCount()), nvl(row.getTotalCount()))))
        .toList();
  }

  @Override
  public List<TrendPoint> trend(long monitorId, LocalDateTime trendStart, LocalDateTime reportEnd) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("monitorId", monitorId); params.put("trendStart", trendStart); params.put("reportEnd", reportEnd);
    return analyticsDao.selectTrend(params).stream()
        .map(row -> new TrendPoint(row.getReportDate(), row.getDimension(), nvl(row.getTotalCount()), nvl(row.getPassedCount()),
            nvl(row.getIssueCount()), rate(nvl(row.getPassedCount()), nvl(row.getTotalCount()))))
        .toList();
  }

  @Override
  public List<ColumnReport> columns(long monitorId, LocalDateTime reportStart, LocalDateTime reportEnd) {
    return analyticsDao.selectColumns(reportParams(monitorId, reportStart, reportEnd)).stream()
        .map(row -> new ColumnReport(row.getColumnName(), row.getDimension(), nvl(row.getTotalCount()), nvl(row.getPassedCount()),
            nvl(row.getIssueCount()), rate(nvl(row.getPassedCount()), nvl(row.getTotalCount()))))
        .toList();
  }

  @Override public long countOperationLogs(long monitorId) { return analyticsDao.countOperationLogs(monitorId); }

  @Override
  public List<OperationLog> operationLogs(long monitorId, int current, int pageSize) {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("monitorId", monitorId); params.put("limit", pageSize); params.put("offset", (current - 1L) * pageSize);
    return analyticsDao.selectOperationLogs(params).stream()
        .map(row -> new OperationLog(row.getLogId(), row.getOperatorName(), row.getOperationTime(), row.getActionType(), row.getActionContent()))
        .toList();
  }

  private Map<String, Object> reportParams(long monitorId, LocalDateTime start, LocalDateTime end) {
    Map<String, Object> params = new LinkedHashMap<>(); params.put("monitorId", monitorId); params.put("reportStart", start); params.put("reportEnd", end); return params;
  }
  private static int nvl(Integer value) { return value == null ? 0 : value; }
  private static double rate(int passed, int total) { return total <= 0 ? 0D : Math.round((passed * 10000D) / total) / 100D; }
}
