package io.yak.ops.common.bean.dto.quality;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import io.yak.ops.common.enums.quality.QualityEnums.RuleScope;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;

/** 执行工作台查询请求。 */
public final class QualityExecutionWorkspaceDTO {
  private QualityExecutionWorkspaceDTO() {}

  public record PageRequest(
      @Min(1) Integer current,
      @Min(1) @Max(100) Integer pageSize,
      String keyword,
      String objectKeyword,
      Long dataSourceId,
      Long monitorId,
      ExecutionStatus executionStatus,
      CheckResult checkResult,
      TriggerType triggerType,
      Boolean hasIssues,
      String dimension,
      RuleScope scope,
      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime queuedAfter,
      @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime queuedBefore) {
    public int normalizedCurrent() { return current == null ? 1 : current; }
    public int normalizedPageSize() { return pageSize == null ? 20 : pageSize; }
  }
}
