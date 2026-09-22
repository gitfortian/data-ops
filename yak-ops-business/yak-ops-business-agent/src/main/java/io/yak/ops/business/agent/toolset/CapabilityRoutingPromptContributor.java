package io.yak.ops.business.agent.toolset;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AGT-1：能力路由决策树进 system prompt。
 *
 * <p>固定路由规则（OpenBKN 方法论对标）：取数/反问/拒绝三类意图按顺序判定，
 * 避免模型在 run_dataset_query 与 request_clarification 之间自由发挥导致误路由。</p>
 */
@Slf4j
@Component
@ConditionalOnAgentEnabled
public class CapabilityRoutingPromptContributor implements AgentSystemPromptContributor {

  @Override
  public String contribute() {
    try {
      return """
          能力路由决策树（取数/答题前按顺序自问）：
          1. 写意图拦截：问题要求改变业务事实（写入/更新/触发作业/审批）→ 明确告知平台当前只读、无写出口，建议走人工流程；禁止调用任何查询工具模拟写操作。
          2. 口径二义兜底：指标口径不明确、问题跨多个数据集/多步场景、缺关键参数 → 先 get_dataset_fields 查清可用字段；仍无法确定则用 request_clarification 反问，不要猜测口径。
          3. 明细/聚合取数 → run_dataset_query：字段引用必须来自 get_dataset_fields 返回的 fieldId。
          4. 相对时间（同比/环比/近N天）→ 先用 current_date_info 取当前日期事实，再换算后传入查询。""";
    } catch (Exception e) {
      log.debug("capability routing prompt contribution skipped: {}", e.getMessage());
      return "";
    }
  }
}
