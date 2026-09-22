package io.yak.ops.business.asset.api;

import java.util.Optional;

/**
 * 资产状态条(PLATFORM_CORE_FLOW M2-1)需要生命周期域提供的只读 TTL 事实。
 * 由 lifecycle 实现;asset 经 ObjectProvider 松耦合消费。
 */
public interface AssetStatusTtlFacts {

  /**
   * @param policyApplied  是否命中生效策略(绑定或分层默认,NONE 之外)
   * @param policyCode     命中的策略编码
   * @param bindingSource  OVERRIDE/LAYER_DEFAULT/LEGACY_LAYER/NONE
   * @param state          TTL 状态机 D5 态:APPLIED/DRIFT/FAILED;无策略时 null
   */
  record TtlFacts(boolean policyApplied, String policyCode, String bindingSource, String state) {}

  /** sourceId 为建模模型主键字符串;仅 MODEL 源适用,其余返回 empty。 */
  Optional<TtlFacts> ttlFacts(String sourceId);
}
