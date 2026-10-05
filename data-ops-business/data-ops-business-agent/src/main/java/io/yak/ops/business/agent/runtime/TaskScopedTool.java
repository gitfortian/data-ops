package io.yak.ops.business.agent.runtime;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Stateless SDK adapter; task/usage belong to the supplied invocation, never the shared registry. */
final class TaskScopedTool extends ToolBase {
  private final AgentTool delegate;

  TaskScopedTool(AgentTool delegate) {
    super(ToolBase.builder().name(delegate.getName())
        .description("load_skill_through_path".equals(delegate.getName())
            ? "只读当前启用管理员技能的 SKILL.md 正文；不激活工具、不读取资源文件、不授予新权限。" : delegate.getDescription())
        .inputSchema("load_skill_through_path".equals(delegate.getName()) ? Map.of("type", "object",
            "properties", Map.of("skillId", Map.of("type", "string", "description", "当前技能提示中的 skill-id"),
                "path", Map.of("type", "string", "enum", java.util.List.of("SKILL.md"))),
            "required", java.util.List.of("skillId", "path"), "additionalProperties", false) : delegate.getParameters())
        .readOnly(delegate.isReadOnly())
        .concurrencySafe(!(delegate instanceof ToolBase base) || base.isConcurrencySafe())
        // External HITL must pass the budget guard before suspension; its body remains unexecuted.
        .externalTool(false)
        .stateInjected(delegate instanceof ToolBase base && base.isStateInjected()));
    this.delegate = delegate;
  }

  @Override public Boolean getStrict() { return delegate.getStrict(); }
  @Override public Map<String, Object> getOutputSchema() { return delegate.getOutputSchema(); }
  @Override public Mono<PermissionDecision> checkPermissions(Map<String, Object> input,
      PermissionContextState permissions) {
    return delegate instanceof ToolBase base ? base.checkPermissions(input, permissions)
        : super.checkPermissions(input, permissions);
  }

  @Override public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
    return Mono.defer(() -> {
      AgentExecutionContext execution = param.getRuntimeContext() == null ? null
          : param.getRuntimeContext().get(AgentExecutionContext.class);
      if (execution == null) return Mono.just(denied("[TOOL_NOT_ALLOWED] 缺少任务执行上下文"));
      try {
        execution.reserveTool(getName());
      } catch (RuntimeException denied) {
        return Mono.just(denied(denied.getMessage()));
      }
      if (delegate instanceof ToolBase base && base.isExternalTool()) {
        return Mono.just(ToolResultBlock.suspended(param.getToolUseBlock()));
      }
      Mono<ToolResultBlock> invocation = "load_skill_through_path".equals(getName())
          ? loadCurrentSkill(param) : delegate.callAsync(param);
      return invocation
          .doOnError(error -> execution.toolFailed(getName()))
          .doOnNext(result -> {
            if (result.getState() == ToolResultState.ERROR) execution.toolFailed(getName());
            if ("load_skill_through_path".equals(getName()) && result.getState() != ToolResultState.DENIED
                && result.getState() != ToolResultState.ERROR) {
              String text = result.getOutput().stream()
                  .filter(io.agentscope.core.message.TextBlock.class::isInstance)
                  .map(block -> ((io.agentscope.core.message.TextBlock) block).getText())
                  .collect(java.util.stream.Collectors.joining("\n"));
              param.getRuntimeContext().put("yak.loadedSkillHash", RuntimeContractHash.hash(text));
            }
          });
    });
  }

  private Mono<ToolResultBlock> loadCurrentSkill(ToolCallParam param) {
    return Mono.fromSupplier(() -> {
      var repository = param.getRuntimeContext().get(io.agentscope.core.skill.repository.AgentSkillRepository.class);
      if (repository == null || !"SKILL.md".equals(param.getInput().get("path"))) {
        return denied("[SKILL_NOT_AVAILABLE] 当前仅支持启用技能的 SKILL.md 正文");
      }
      // Live enabled catalog prevents an SDK helper captured before disable/delete from serving stale content.
      return repository.getAllSkills().stream()
          .filter(skill -> skill.getSkillId().equals(param.getInput().get("skillId")))
          .findFirst().map(skill -> ToolResultBlock.text(skill.getSkillContent()))
          .orElseGet(() -> denied("[SKILL_NOT_AVAILABLE] 技能已停用、删除或当前不可读"));
    }).onErrorReturn(denied("[SKILL_UNAVAILABLE] 技能来源暂不可用"));
  }

  private static ToolResultBlock denied(String reason) {
    return ToolResultBlock.text(reason).withState(ToolResultState.DENIED);
  }
}
