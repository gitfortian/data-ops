package io.yak.ops.business.agent.toolset;

import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.ModelMappingContext;
import io.yak.ops.business.agent.domain.ModelMappingProposal;
import io.yak.ops.business.agent.domain.ModelMappingSuggestion;
import io.yak.ops.business.agent.domain.ModelMappingTarget;
import io.yak.ops.business.agent.gateway.ModelMappingGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class ModelMappingTools implements AgentToolBox {
  private final AgentToolExecution execution;
  private final ModelMappingGateway gateway;
  @io.agentscope.core.tool.Tool(name = "get_model_mapping_context", description = "只读绑定目标字段、现有映射和用户指定源表的最多50个字段；不能切换对象或读取数据行。")
  public ModelMappingContext context(RuntimeContext context) {
    var target = AgentToolExecution.state(context).target();
    if (target == null || target.modelMapping() == null) throw new IllegalArgumentException("缺少模型映射目标");
    return prepare(context, target.modelMapping());
  }
  public ModelMappingContext prepare(RuntimeContext context, ModelMappingTarget target) {
    return execution.call(context, "get_model_mapping_context", () -> gateway.prepare(target));
  }
  public ModelMappingSuggestion validate(RuntimeContext context, ModelMappingTarget target, ModelMappingContext source,
      ModelMappingProposal value, int skillVersion, String skillHash) {
    return execution.call(context, "get_model_mapping_context", () -> gateway.validate(target, source, value, skillVersion, skillHash));
  }
  public ModelMappingSuggestion revalidate(ModelMappingSuggestion value) { return gateway.revalidate(value); }
}
