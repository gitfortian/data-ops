package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import io.yak.ops.business.agent.toolset.AgentSystemPromptContributor;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 系统提示词组装管道（onSystemPrompt 拦截点）：在框架基础提示词之上按序追加各能力域贡献，
 * 单个贡献者失败静默降级；贡献者经 boundedElastic 卸载，支持阻塞型装配。
 *
 * <p>由 {@code AgentRuntime} 装配（非 Spring bean）。</p>
 */
public class SystemPromptAssemblyMiddleware implements MiddlewareBase {

  private final List<AgentSystemPromptContributor> contributors;

  public SystemPromptAssemblyMiddleware(List<AgentSystemPromptContributor> contributors) {
    this.contributors = List.copyOf(contributors);
  }

  @Override
  public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
    if (contributors.isEmpty()) {
      return Mono.just(currentPrompt == null ? "" : currentPrompt);
    }
    return Flux.fromIterable(contributors)
        .concatMap(
            contributor ->
                Mono.fromCallable(contributor::contribute)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(
                        e -> {
                          // 提示词增强是 best-effort：失败静默降级不阻断推理
                          return Mono.empty();
                        }))
        .filter(section -> section != null && !section.isBlank())
        .reduce(
            currentPrompt == null ? "" : currentPrompt,
            (assembled, section) -> assembled + "\n\n" + section.strip());
  }
}
