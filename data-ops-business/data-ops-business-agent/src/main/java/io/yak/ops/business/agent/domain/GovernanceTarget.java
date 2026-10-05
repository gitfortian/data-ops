package io.yak.ops.business.agent.domain;

/** Selected source object, never an authorization grant or an execution identity. */
public record GovernanceTarget(Long assetId, String qualityExecutionNo) {
  public GovernanceTarget {
    if ((assetId == null) == (qualityExecutionNo == null)) {
      throw new IllegalArgumentException("请选择一个资产或质量执行记录");
    }
    if (assetId != null && assetId <= 0) throw new IllegalArgumentException("资产编号无效");
    if (qualityExecutionNo != null && !qualityExecutionNo.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("质量执行编号无效");
    }
  }
}
