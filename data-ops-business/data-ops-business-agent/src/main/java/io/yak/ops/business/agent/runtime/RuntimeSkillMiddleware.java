package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.skill.DynamicSkillMiddleware;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.tool.Toolkit;
import java.util.List;
import reactor.core.publisher.Mono;

/** Captures the actual prompt fragment returned by the SDK, rather than re-reading mutable definitions. */
final class RuntimeSkillMiddleware extends DynamicSkillMiddleware {
  private final AgentSkillRepository repository;
  RuntimeSkillMiddleware(AgentSkillRepository repository, Toolkit toolkit) {
    super(List.of(repository), toolkit);
    this.repository = repository;
  }
  RuntimeSkillMiddleware(AgentSkillRepository repository, Toolkit toolkit, java.nio.file.Path workDir) {
    super(List.of(repository), toolkit, null, false, workDir);
    this.repository = repository;
  }

  @Override public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
    if (repository instanceof ScenarioSkillScope scope) scope.requireCurrent();
    context.put(AgentSkillRepository.class, repository);
    return super.onSystemPrompt(agent, context, currentPrompt).map(prompt -> {
      TaskToolPolicyMiddleware.guardTools(agent.getToolkit());
      if (!prompt.equals(currentPrompt)) prompt += "\n平台技能加载仅允许当前启用技能的 SKILL.md，不能读取资源文件、激活额外工具或改变任务授权。";
      String fragment = prompt.startsWith(currentPrompt) ? prompt.substring(currentPrompt.length()) : prompt;
      context.put("yak.skillPromptHash", RuntimeContractHash.hash(fragment));
      return prompt;
    });
  }
}
