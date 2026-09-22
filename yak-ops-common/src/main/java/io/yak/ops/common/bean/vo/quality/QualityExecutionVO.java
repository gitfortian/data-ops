package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量执行响应契约。 */
public final class QualityExecutionVO {
  private QualityExecutionVO() {}

  public record Status(
      String executionNo,
      ExecutionStatus executionStatus,
      CheckResult checkResult,
      @QualityDateTimeFormat LocalDateTime startedAt,
      @QualityDateTimeFormat LocalDateTime finishedAt,
      Long durationMs,
      String errorMessage) {}

  public record RuleExecution(
      Long id,
      Long ruleId,
      String ruleName,
      String templateCode,
      RuleType ruleType,
      String columnName,
      CheckResult checkResult,
      String metricValue,
      String expectedValue,
      String executedSql,
      String errorMessage,
      Long durationMs) {}

  public record ListItem(
      String executionNo,
      Long monitorId,
      String monitorName,
      String dataSourceName,
      String objectName,
      ExecutionStatus executionStatus,
      CheckResult checkResult,
      int totalRules,
      int passedRules,
      int failedRules,
      int errorRules,
      String operator,
      @QualityDateTimeFormat LocalDateTime queuedAt,
      @QualityDateTimeFormat LocalDateTime startedAt,
      @QualityDateTimeFormat LocalDateTime finishedAt,
      Long durationMs,
      String errorMessage) {}

  public record Detail(
      String executionNo,
      Long monitorId,
      String monitorName,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String objectName,
      ExecutionStatus executionStatus,
      CheckResult checkResult,
      int totalRules,
      int passedRules,
      int failedRules,
      int errorRules,
      String operator,
      @QualityDateTimeFormat LocalDateTime queuedAt,
      @QualityDateTimeFormat LocalDateTime startedAt,
      @QualityDateTimeFormat LocalDateTime finishedAt,
      Long durationMs,
      String errorMessage,
      List<RuleExecution> rules) {}

  public record Page(List<ListItem> records, long total, int current, int pageSize) {}
}
