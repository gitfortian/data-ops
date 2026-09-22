package io.yak.ops.common.bean.dto.quality;

import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.ExecutionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 数据质量执行查询请求。 */
public final class QualityExecutionDTO {
  private QualityExecutionDTO() {}

  public record PageRequest(
      @Min(1) Integer current,
      @Min(1) @Max(100) Integer pageSize,
      String keyword,
      Long monitorId,
      ExecutionStatus executionStatus,
      CheckResult checkResult) {
    public int normalizedCurrent() { return current == null ? 1 : current; }
    public int normalizedPageSize() { return pageSize == null ? 20 : pageSize; }
  }
}
