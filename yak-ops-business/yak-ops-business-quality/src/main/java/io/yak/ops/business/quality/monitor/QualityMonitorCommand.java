package io.yak.ops.business.quality.monitor;

import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import java.math.BigDecimal;
import java.util.List;

/** Typed command boundary for quality monitor writes. */
public final class QualityMonitorCommand {
  private QualityMonitorCommand() {}

  public record Save(
      String name,
      String description,
      Long dataSourceId,
      String dataSourceName,
      String databaseName,
      String schemaName,
      String tableName,
      String whereClause,
      String owner,
      Boolean enabled,
      Settings settings,
      List<Rule> rules) {
    public Save {
      rules = rules == null ? List.of() : List.copyOf(rules);
    }
  }

  public record Settings(
      RunMode runMode,
      ScheduleFrequency scheduleFrequency,
      String scheduleTime,
      ScheduleWeekday scheduleWeekday,
      String cronExpression,
      RuleFailureAction ruleFailureAction,
      Boolean notifyEnabled,
      NotifyChannel notifyChannel,
      String notifyTarget,
      AlertLevel alertLevel,
      Boolean scheduleEnabled) {}

  public record Rule(
      Long templateId,
      String name,
      String columnName,
      String operator,
      BigDecimal threshold,
      BigDecimal thresholdEnd,
      List<String> enumValues,
      String customSql,
      Boolean enabled) {
    public Rule {
      enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }
  }
}
