package io.yak.ops.business.agent.telemetry;

import java.util.Set;

/**
 * 观测类型声明（设计稿 §4.2）：一种观测维度（kind）的全部元数据，
 * 采集校验 / 载荷策略 / 父链规则 / 渲染投影四处自动消费。
 *
 * <p>新增观测维度 = 在 {@link AgentKindRegistry} 注册一份 KindSpec——
 * 不改采集器代码、不改表结构、不改前端渲染组件；未注册 kind 在落库前被拒绝（治理准入）。</p>
 */
public record KindSpec(
    String kind,
    String title,
    Set<String> allowedStatus,
    PayloadMode requestMode,
    PayloadMode responseMode,
    ParentRule parentRule,
    RenderHint renderHint) {

  /** 载荷档位（与 PayloadEnvelope 模式对应；NONE 表示该侧不落载荷）。 */
  public enum PayloadMode {
    INLINE,
    SUMMARY_HASH,
    HASH_ONLY,
    OMITTED,
    NONE
  }

  /** 父链规则：TOOL 类指向最近一次 LLM_CALL，事件型挂轮次根，或无父。 */
  public enum ParentRule {
    TURN,
    LAST_LLM,
    NONE
  }
}
