package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量监控工作台响应契约。 */
public final class QualityWorkspaceVO {
  private QualityWorkspaceVO() {}

  public record Stats(
      int ruleCount,
      int enabledRuleCount,
      int executionCount,
      int issueExecutionCount,
      @QualityDateTimeFormat LocalDateTime latestExecutionTime) {}

  public record MonitorWorkspace(
      QualityMonitorVO.Detail monitor,
      QualityMonitorVO.Settings settings,
      Stats stats) {}

  public record ReportOverview(
      int totalRules,
      int enabledRules,
      int executedRules,
      int issueRules,
      int errorRules,
      double passRate) {}

  public record DimensionReport(
      String dimension,
      int total,
      int passed,
      int notPassed,
      int errors,
      double passRate) {}

  public record TrendPoint(
      LocalDate date,
      String dimension,
      int total,
      int passed,
      int issues,
      double passRate) {}

  public record ColumnReport(
      String columnName,
      String dimension,
      int total,
      int passed,
      int issues,
      double passRate) {}

  public record MonitorReport(
      LocalDate reportDate,
      LocalDate trendStartDate,
      ReportOverview overview,
      List<DimensionReport> dimensions,
      List<TrendPoint> trend,
      List<ColumnReport> columns) {}

  public record OperationLogItem(
      String id,
      String operator,
      @QualityDateTimeFormat LocalDateTime operationTime,
      String actionType,
      String content) {}

  public record OperationLogPage(
      List<OperationLogItem> records,
      long total,
      int current,
      int pageSize) {}
}
