package io.yak.ops.common.bean.dto.quality;

import io.yak.ops.common.enums.quality.QualityEnums.AlertLevel;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.NotifyChannel;
import io.yak.ops.common.enums.quality.QualityEnums.RuleFailureAction;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleFrequency;
import io.yak.ops.common.enums.quality.QualityEnums.ScheduleWeekday;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 数据质量监控请求契约。 */
public final class QualityMonitorDTO {
  private QualityMonitorDTO() {}

  public record PageRequest(
      @Min(1) Integer current,
      @Min(1) @Max(100) Integer pageSize,
      String keyword,
      Long dataSourceId,
      String databaseName,
      String schemaName,
      String tableName,
      Boolean enabled,
      CheckResult lastResult) {
    public int normalizedCurrent() { return current == null ? 1 : current; }
    public int normalizedPageSize() { return pageSize == null ? 20 : pageSize; }
  }

  public record SaveRuleRequest(
      @NotNull Long templateId,
      @NotBlank @Size(max = 100) String name,
      @Size(max = 256) String columnName,
      String operator,
      BigDecimal threshold,
      BigDecimal thresholdEnd,
      List<@Size(max = 256) String> enumValues,
      @Size(max = 20000) String customSql,
      Boolean enabled) {}

  /**
   * Monitor settings keep the old friendly schedule fields for API compatibility.
   * New clients should use scheduleEnabled + cronExpression as the canonical schedule contract.
   */
  public record SettingsRequest(
      RunMode runMode,
      ScheduleFrequency scheduleFrequency,
      @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "执行时间格式必须为 HH:mm")
      String scheduleTime,
      ScheduleWeekday scheduleWeekday,
      @Size(max = 128) String cronExpression,
      RuleFailureAction ruleFailureAction,
      Boolean notifyEnabled,
      NotifyChannel notifyChannel,
      @Size(max = 1000) String notifyTarget,
      AlertLevel alertLevel,
      Boolean scheduleEnabled) {}

  public record SaveRequest(
      @NotBlank @Size(max = 100) String name,
      @Size(max = 500) String description,
      @NotNull Long dataSourceId,
      @NotBlank @Size(max = 128) String dataSourceName,
      @Size(max = 128) String databaseName,
      @Size(max = 128) String schemaName,
      @NotBlank @Size(max = 256) String tableName,
      @Size(max = 4000) String whereClause,
      @NotBlank @Size(max = 128) String owner,
      Boolean enabled,
      @Valid SettingsRequest settings,
      @NotEmpty List<@Valid SaveRuleRequest> rules) {}
}
