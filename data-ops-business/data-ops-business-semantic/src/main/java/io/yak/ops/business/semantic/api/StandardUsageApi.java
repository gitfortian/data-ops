package io.yak.ops.business.semantic.api;

/**
 * Usage-reporting SPI (ticket 42, push-based): consumers report APPLY/BYPASS
 * at their action points; aggregation stays server-side in semantic.
 * Reporting is fail-open on the caller side — record() failures must never
 * block the caller's business flow. Implementations must not leak internal
 * types.
 */
public interface StandardUsageApi {

  /** 记录一次引用/绕过事件;实现不得抛出阻断调用方的异常。 */
  void record(UsageEvent event);

  /** 单标准的采纳/绕过计数(服务端聚合)。 */
  UsageSummary summary(Long standardId);

  record UsageEvent(
      Long standardId, String usageType, String scene, String modelRef, String operatedBy) {}

  record UsageSummary(Long standardId, long applyCount, long bypassCount) {

    /** 反哺建议:绕过 ≥ 采纳 且 绕过 ≥ 3 → 标准可能有问题(REQUIREMENTS 42)。 */
    public boolean suspicious() {
      return bypassCount >= applyCount && bypassCount >= 3;
    }
  }
}
