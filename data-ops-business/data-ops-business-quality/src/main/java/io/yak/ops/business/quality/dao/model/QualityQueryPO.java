package io.yak.ops.business.quality.dao.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 复杂联查使用的只读持久化投影，不作为 API 契约。 */
public final class QualityQueryPO {
  private QualityQueryPO() {}

  @Data
  public static class TemplateRow {
    private Long id;
    private String templateCode;
    private String templateName;
    private String description;
    private String ruleType;
    private String ruleScope;
    private String qualityDimension;
    private String parameterSchemaJson;
    private Boolean builtin;
    private Boolean enabled;
    private Integer sortOrder;
    private Long ruleCount;
    private Long folderId;
    private String folderName;
    private String templateSql;
    private String setFlag;
    private String checkType;
    private String checkMethod;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
  }

  @Data
  public static class FolderRow {
    private Long id;
    private Long parentId;
    private String folderName;
    private Integer sortOrder;
    private Long templateCount;
    private Long childCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
  }

  @Data
  public static class MonitorRow {
    private Long id;
    private String monitorName;
    private String description;
    private Long dataSourceId;
    private String dataSourceName;
    private String databaseName;
    private String schemaName;
    private String tableName;
    private String whereClause;
    private String owner;
    private Boolean enabled;
    private String lastResult;
    private String lastExecutionNo;
    private LocalDateTime lastRunTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer ruleCount;
  }

  @Data
  public static class TableMonitorSummaryRow {
    private String tableName;
    private Long monitorId;
    private String monitorName;
    private Integer monitorCount;
    private Integer ruleCount;
    private Integer enabledMonitorCount;
    private String lastExecutionNo;
    private String lastResult;
    private LocalDateTime lastRunTime;
  }

  @Data
  public static class AlertEventRow {
    private Long id;
    private Long monitorId;
    private String monitorName;
    private String executionNo;
    private String checkResult;
    private String alertLevel;
    private String notifyChannel;
    private String deliveryStatus;
    private String alertMessage;
    private LocalDateTime createdAt;
  }

  @Data
  public static class TableAssetRow {
    private Long id;
    private Long dataSourceId;
    private String dataSourceName;
    private String databaseName;
    private String schemaName;
    private String tableName;
    private String tableType;
    private String remarks;
    private Long monitorId;
    private String monitorName;
    private Integer monitorCount;
    private Integer ruleCount;
    private String lastResult;
    private LocalDateTime lastRunTime;
    private String registeredBy;
    private LocalDateTime registeredAt;
  }

  @Data
  public static class RuleExecutionWorkspaceRow {
    private Long ruleExecutionId;
    private Long ruleId;
    private String executionNo;
    private Long monitorId;
    private String monitorName;
    private Long dataSourceId;
    private String dataSourceName;
    private String databaseName;
    private String schemaName;
    private String tableName;
    private String objectName;
    private String ruleName;
    private String templateCode;
    private String ruleType;
    private String columnName;
    private String triggerType;
    private String executionStatus;
    private String ruleCheckResult;
    private String metricValue;
    private String expectedValue;
    private String operatorName;
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long ruleDurationMs;
    private String ruleErrorMessage;
  }

  @Data
  public static class WorkspaceStatsRow {
    private Integer ruleCount;
    private Integer enabledRuleCount;
    private Integer executionCount;
    private Integer issueExecutionCount;
    private LocalDateTime latestExecutionTime;
  }

  @Data
  public static class ReportOverviewRow {
    private Integer totalRules;
    private Integer enabledRules;
    private Integer executedRules;
    private Integer issueRules;
    private Integer errorRules;
    private Integer passedRules;
  }

  @Data
  public static class DimensionReportRow {
    private String dimension;
    private Integer totalCount;
    private Integer passedCount;
    private Integer notPassedCount;
    private Integer errorCount;
  }

  @Data
  public static class TrendPointRow {
    private LocalDate reportDate;
    private String dimension;
    private Integer totalCount;
    private Integer passedCount;
    private Integer issueCount;
  }

  @Data
  public static class ColumnReportRow {
    private String columnName;
    private String dimension;
    private Integer totalCount;
    private Integer passedCount;
    private Integer issueCount;
  }

  @Data
  public static class OperationLogRow {
    private String logId;
    private String operatorName;
    private LocalDateTime operationTime;
    private String actionType;
    private String actionContent;
  }
}
