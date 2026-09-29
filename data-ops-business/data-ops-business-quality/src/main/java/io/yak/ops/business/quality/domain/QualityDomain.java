package io.yak.ops.business.quality.domain;

import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckMethod;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.CheckType;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 数据质量业务领域模型。HTTP DTO/VO 与数据库 PO 不进入该层。 */
public final class QualityDomain {
  private QualityDomain() {}

  public record Template(
      Long id, String code, String name, String description,
      RuleType ruleType, RuleScope scope, String dimension, String parameterSchema,
      boolean builtin, boolean enabled, long ruleCount, int sortOrder) {}

  public record Rule(
      Long id, Long monitorId, Long templateId, String templateCode, String name,
      RuleType ruleType, RuleScope scope, String dimension, String columnName,
      String operator, BigDecimal threshold, BigDecimal thresholdEnd,
      List<String> enumValues, String customSql, boolean enabled, int sortOrder) {}

  public record Monitor(
      Long id, String name, String description, Long dataSourceId, String dataSourceName,
      String databaseName, String schemaName, String tableName, String whereClause,
      String owner, boolean enabled, CheckResult lastResult, String lastExecutionNo,
      LocalDateTime lastRunTime, LocalDateTime createTime, LocalDateTime updateTime,
      int ruleCount, List<Rule> rules) {}

  public record MonitorSettings(
      RunMode runMode, ScheduleFrequency scheduleFrequency, String scheduleTime,
      ScheduleWeekday scheduleWeekday, String cronExpression, LocalDateTime nextRunTime,
      RuleFailureAction ruleFailureAction, boolean notifyEnabled, NotifyChannel notifyChannel,
      String notifyTarget, AlertLevel alertLevel) {}

  public record TableMonitorSummary(
      String tableName, Long monitorId, String monitorName, int monitorCount,
      int ruleCount, int enabledMonitorCount, String lastExecutionNo,
      CheckResult lastResult, LocalDateTime lastRunTime) {
    public TableMonitorSummary(
        String tableName, Long monitorId, String monitorName, int monitorCount,
        int ruleCount, CheckResult lastResult, LocalDateTime lastRunTime) {
      this(tableName, monitorId, monitorName, monitorCount, ruleCount, 0, null,
          lastResult, lastRunTime);
    }
  }

  /** 告警事件只读投影：投递状态沿用存储原值(RECORDED/PENDING/FAILED),不臆造枚举语义。 */
  public record AlertEvent(
      long id, Long monitorId, String monitorName, String executionNo,
      CheckResult checkResult, AlertLevel alertLevel, NotifyChannel notifyChannel,
      String deliveryStatus, String alertMessage, LocalDateTime createdAt) {}

  public record TableAsset(
      Long id, Long dataSourceId, String dataSourceName, String databaseName,
      String schemaName, String tableName, String tableType, String remarks,
      Long monitorId, String monitorName, int monitorCount, int ruleCount,
      CheckResult lastResult, LocalDateTime lastRunTime, String registeredBy,
      LocalDateTime registeredAt) {}

  public record TableAssetTarget(String databaseName, String schemaName, String tableName) {}

  public record Execution(
      Long id, String executionNo, Long monitorId, String monitorName, Long dataSourceId,
      String dataSourceName, String databaseName, String schemaName, String tableName,
      String objectName, TriggerType triggerType, ExecutionStatus executionStatus,
      CheckResult checkResult, int totalRules, int passedRules, int failedRules,
      int errorRules, String operator, LocalDateTime queuedAt, LocalDateTime startedAt,
      LocalDateTime finishedAt, Long durationMs, String errorMessage,
      List<RuleExecution> rules) {}

  public record RuleExecution(
      Long id, Long ruleId, String ruleName, String templateCode, RuleType ruleType,
      String columnName, CheckResult checkResult, String metricValue, String expectedValue,
      String executedSql, String errorMessage, Long durationMs, LocalDateTime createdAt) {}

  public record RuleExecutionWorkspaceItem(
      Long id, Long ruleId, String executionNo, Long monitorId, String monitorName,
      Long dataSourceId, String dataSourceName, String databaseName, String schemaName,
      String tableName, String objectName, String ruleName, String templateCode,
      RuleType ruleType, RuleScope scope, String dimension, String columnName,
      TriggerType triggerType, ExecutionStatus executionStatus, CheckResult checkResult,
      String metricValue, String expectedValue, String operator, LocalDateTime queuedAt,
      LocalDateTime startedAt, LocalDateTime finishedAt, Long durationMs, String errorMessage) {}

  public record CustomTemplate(
      Long id, String code, String name, String description, RuleType ruleType,
      RuleScope scope, String dimension, String parameterSchema, boolean builtin,
      boolean enabled, long ruleCount, int sortOrder, Long folderId, String folderName,
      String templateSql, String setFlag, CheckType checkType, CheckMethod checkMethod,
      String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) {}

  public record TemplateFolder(
      Long id, Long parentId, String name, int sortOrder, long templateCount,
      long childCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}

  public record WorkspaceStats(
      int ruleCount, int enabledRuleCount, int executionCount, int issueExecutionCount,
      LocalDateTime latestExecutionTime) {}
  public record ReportOverview(
      int totalRules, int enabledRules, int executedRules, int issueRules,
      int errorRules, double passRate) {}
  public record DimensionReport(
      String dimension, int total, int passed, int notPassed, int errors, double passRate) {}
  public record TrendPoint(
      LocalDate date, String dimension, int total, int passed, int issues, double passRate) {}
  public record ColumnReport(
      String columnName, String dimension, int total, int passed, int issues, double passRate) {}
  public record OperationLog(
      String id, String operator, LocalDateTime operationTime, String actionType, String content) {}

  public record MonitorSpec(
      String name, String description, long dataSourceId, String dataSourceName,
      String databaseName, String schemaName, String tableName, String whereClause,
      String owner, boolean enabled) {}

  public record MonitorSettingsSpec(
      RunMode runMode, ScheduleFrequency scheduleFrequency, String scheduleTime,
      ScheduleWeekday scheduleWeekday, String cronExpression, LocalDateTime nextRunTime,
      RuleFailureAction ruleFailureAction, boolean notifyEnabled, NotifyChannel notifyChannel,
      String notifyTarget, AlertLevel alertLevel) {}

  public record RuleSpec(
      long templateId, String templateCode, String name, RuleType ruleType,
      RuleScope scope, String dimension, String columnName, ComparisonOperator operator,
      BigDecimal threshold, BigDecimal thresholdEnd, List<String> enumValues,
      String customSql, boolean enabled) {}

  public record TableAssetSpec(
      long dataSourceId, String dataSourceName, String databaseName, String schemaName,
      String tableName, String tableType, String remarks, String registeredBy) {}

  public record AlertEventSpec(
      long monitorId, String executionNo, CheckResult checkResult, AlertLevel alertLevel,
      NotifyChannel notifyChannel, String notifyTarget, String deliveryStatus,
      String alertMessage, String errorMessage, LocalDateTime createdAt) {}

  public record RuleExecutionSpec(
      long executionId, long ruleId, String ruleName, String templateCode,
      RuleType ruleType, String columnName, CheckResult checkResult, String metricValue,
      String expectedValue, String executedSql, String errorMessage, long durationMs) {}

  public record FolderSpec(Long parentId, String name, String operator) {}
  public record CustomTemplateSpec(
      String code, String name, String description, String dimension, String parameterSchema,
      Long folderId, String templateSql, String setFlag, CheckType checkType,
      CheckMethod checkMethod, String operator) {}
}
