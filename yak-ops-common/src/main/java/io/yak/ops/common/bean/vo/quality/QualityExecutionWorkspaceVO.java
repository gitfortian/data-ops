package io.yak.ops.common.bean.vo.quality;

import io.yak.ops.common.annotation.quality.QualityDateTimeFormat;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.LogLevel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量执行工作台响应契约。 */
public final class QualityExecutionWorkspaceVO {
  private QualityExecutionWorkspaceVO() {}

  public record ExecutionListItem(
      String executionNo,
      Long monitorId,
      String monitorName,
      Long dataSourceId,
      String dataSourceName,
      String objectName,
      TriggerType triggerType,
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

  public record RuleExecutionListItem(
      Long id,
      Long ruleId,
      String executionNo,
      Long monitorId,
      String monitorName,
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String objectName,
      String ruleName,
      String templateCode,
      RuleType ruleType,
      RuleScope scope,
      String dimension,
      String columnName,
      TriggerType triggerType,
      ExecutionStatus executionStatus,
      CheckResult checkResult,
      String metricValue,
      String expectedValue,
      String operator,
      @QualityDateTimeFormat LocalDateTime queuedAt,
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
      RuleScope scope,
      String dimension,
      String columnName,
      CheckResult checkResult,
      String metricValue,
      String expectedValue,
      String executedSql,
      String errorMessage,
      Long durationMs,
      @QualityDateTimeFormat LocalDateTime createdAt) {}

  public record ExecutionDetail(
      String executionNo,
      Long monitorId,
      String monitorName,
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String objectName,
      TriggerType triggerType,
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

  public record ExecutionPage(List<ExecutionListItem> records, long total, int current, int pageSize) {}
  public record RuleExecutionPage(List<RuleExecutionListItem> records, long total, int current, int pageSize) {}

  public record LogLine(
      @QualityDateTimeFormat LocalDateTime timestamp,
      LogLevel level,
      String stage,
      String message) {}

  public record LogView(String executionNo, List<LogLine> lines) {}
}
