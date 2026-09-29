package io.yak.ops.business.quality.domain.execution;

import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.ComparisonOperator;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.RuleType;
import java.math.BigDecimal;
import java.util.List;

/** Immutable execution snapshot captured when a quality check is enqueued. */
public record QualityExecutionPlan(
    long projectId,
    long executionId,
    String executionNo,
    MonitorSnapshot monitor,
    List<RuleSnapshot> rules,
    RuleFailureAction ruleFailureAction,
    boolean notifyEnabled,
    NotifyChannel notifyChannel,
    String notifyTarget,
    AlertLevel alertLevel) {

  public QualityExecutionPlan {
    if (projectId <= 0L) throw new IllegalArgumentException("质量执行 Project ID 不合法");
    if (executionId <= 0L) throw new IllegalArgumentException("质量执行 ID 不合法");
    if (executionNo == null || executionNo.isBlank()) {
      throw new IllegalArgumentException("质量执行编号不能为空");
    }
    if (monitor == null) throw new IllegalArgumentException("质量监控快照不能为空");
    rules = rules == null ? List.of() : List.copyOf(rules);
    ruleFailureAction =
        ruleFailureAction == null ? RuleFailureAction.CONTINUE : ruleFailureAction;
    notifyChannel = notifyChannel == null ? NotifyChannel.MESSAGE : notifyChannel;
    alertLevel = alertLevel == null ? AlertLevel.WARNING : alertLevel;
  }

  public record MonitorSnapshot(
      long id,
      String name,
      long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String whereClause,
      String owner) {}

  public record RuleSnapshot(
      long id,
      long templateId,
      String templateCode,
      String name,
      RuleType ruleType,
      RuleScope scope,
      String dimension,
      String columnName,
      ComparisonOperator operator,
      BigDecimal threshold,
      BigDecimal thresholdEnd,
      List<String> enumValues,
      String customSql) {
    public RuleSnapshot {
      enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
  }
}
