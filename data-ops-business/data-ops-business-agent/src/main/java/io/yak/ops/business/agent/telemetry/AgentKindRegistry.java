package io.yak.ops.business.agent.telemetry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 观测类型注册表（设计稿 §4.2）：yak_agent_step.kind 的单一真相源。
 *
 * <p>治理规则：落库前必须命中注册表，未注册 kind 拒绝并告警——防"定义了没人用 /
 * 用了没人管"再次发生；DDL 注释不再独立维护 kind 枚举（V9 起以本表为准）。
 * 注册表与 DDL 的一致性由启动校验（O1 架构守护）兜底。</p>
 */
public final class AgentKindRegistry {

  private static final Map<String, KindSpec> SPECS = new LinkedHashMap<>();

  public static final String KIND_LLM_CALL = "LLM_CALL";
  public static final String KIND_TOOL_CALL = "TOOL_CALL";
  public static final String KIND_GUARD = "GUARD";
  public static final String KIND_HITL = "HITL";
  public static final String KIND_COMPACTION = "COMPACTION";
  public static final String KIND_TURN_SUMMARY = "TURN_SUMMARY";
  /** 长期记忆线（data-agent-long-term-memory-design.md）：M1 已接线 kind（FLUSH/RECALL）有生产者；
   *  KIND_MEMORY_CONSOLIDATE 为 M2 巩固管线预留——管线尚未接线（合并/固化/归档调度未实现），
   *  当前无任何生产者，见 ARCHITECTURE §Memory「M2 规划中 WIP」。 */
  public static final String KIND_MEMORY_FLUSH = "MEMORY_FLUSH";
  public static final String KIND_MEMORY_RECALL = "MEMORY_RECALL";
  public static final String KIND_MEMORY_CONSOLIDATE = "MEMORY_CONSOLIDATE";

  static {
    register(new KindSpec(
        KIND_LLM_CALL, "模型调用", Set.of("COMPLETED", "FAILED"),
        KindSpec.PayloadMode.SUMMARY_HASH, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.TURN,
        new RenderHint("purple", "robot", 10)));
    register(new KindSpec(
        KIND_TOOL_CALL, "工具调用", Set.of("COMPLETED", "FAILED", "REJECTED"),
        KindSpec.PayloadMode.INLINE, KindSpec.PayloadMode.INLINE, KindSpec.ParentRule.LAST_LLM,
        new RenderHint("blue", "tool", 20)));
    register(new KindSpec(
        KIND_GUARD, "守卫拒绝", Set.of("REJECTED", "FAILED", "COMPLETED"),
        KindSpec.PayloadMode.INLINE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.LAST_LLM,
        new RenderHint("orange", "safety", 30)));
    register(new KindSpec(
        KIND_HITL, "人机协同", Set.of("COMPLETED", "FAILED"),
        KindSpec.PayloadMode.INLINE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.LAST_LLM,
        new RenderHint("gold", "user", 40)));
    register(new KindSpec(
        KIND_COMPACTION, "上下文压缩", Set.of("COMPLETED"),
        KindSpec.PayloadMode.NONE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.TURN,
        new RenderHint("cyan", "compress", 50)));
    register(new KindSpec(
        KIND_TURN_SUMMARY, "轮次汇总", Set.of("COMPLETED"),
        KindSpec.PayloadMode.NONE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.TURN,
        new RenderHint("green", "summary", 60)));
    register(new KindSpec(
        KIND_MEMORY_FLUSH, "长期记忆提取", Set.of("COMPLETED", "FAILED"),
        KindSpec.PayloadMode.INLINE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.LAST_LLM,
        new RenderHint("geekblue", "memory", 70)));
    register(new KindSpec(
        KIND_MEMORY_RECALL, "长期记忆召回", Set.of("COMPLETED", "FAILED"),
        KindSpec.PayloadMode.INLINE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.LAST_LLM,
        new RenderHint("geekblue", "memory", 71)));
    register(new KindSpec(
        KIND_MEMORY_CONSOLIDATE, "长期记忆巩固", Set.of("COMPLETED", "FAILED"),
        KindSpec.PayloadMode.INLINE, KindSpec.PayloadMode.NONE, KindSpec.ParentRule.TURN,
        new RenderHint("geekblue", "memory", 72)));
  }

  private AgentKindRegistry() {}

  /** 落库前准入校验：未注册 kind 抛出（采集侧捕获后告警并放弃，不影响执行事实）。 */
  public static KindSpec require(String kind) {
    KindSpec spec = SPECS.get(kind);
    if (spec == null) {
      throw new IllegalArgumentException("未注册的观测类型（AgentKindRegistry）：kind=" + kind);
    }
    return spec;
  }

  public static boolean isRegistered(String kind) {
    return kind != null && SPECS.containsKey(kind);
  }

  public static Collection<KindSpec> all() {
    return Set.copyOf(SPECS.values());
  }

  private static void register(KindSpec spec) {
    SPECS.put(spec.kind(), spec);
  }
}
