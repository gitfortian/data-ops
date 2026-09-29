package io.yak.ops.business.agent.runtime;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.function.Function;
import reactor.core.publisher.Flux;

/**
 * 项目空间上下文恢复中间件：在工具执行（onActing）时从 RuntimeContext 取出持久化的 projectId，
 * 经 ProjectContextScope.run 在当前线程恢复项目上下文，
 * 使工具内 CurrentProject.requireProjectId() 可用。
 *
 * <p>projectId 由 AgentTurnExecutor 在提交推理时写入 RuntimeContext 的 stringAttributes，
 * 经框架中间件链传播至此——这是唯一能在 reactor 线程恢复项目上下文的接缝点。</p>
 *
 * <p>设计约束：工具执行可能在 reactor 的线程池上进行，
 * doOnSubscribe 仅在订阅线程绑定 ThreadLocal 无法覆盖实际执行线程。
 * 改用 Flux.create 在订阅时经 ProjectContextScope.run 绑定上下文并同步触发内层订阅，
 * 确保同步执行段运行在绑定了上下文的线程上；run 返回即自动归还上下文。
 * 工具若切换线程池执行，则由目标线程自行取上下文（与本绑定方案的边界一致）。</p>
 */
public class ProjectContextMiddleware implements MiddlewareBase {

  /** RuntimeContext stringAttributes key，与 AgentRuntime.streamEvents 写入侧对齐。 */
  static final String ATTR_PROJECT_ID = "yak.projectId";

  private final ProjectContextScope projectContextScope;

  public ProjectContextMiddleware(ProjectContextScope projectContextScope) {
    this.projectContextScope = projectContextScope;
  }

  @Override
  public Flux<AgentEvent> onActing(
      Agent agent,
      RuntimeContext context,
      ActingInput input,
      Function<ActingInput, Flux<AgentEvent>> next) {
    Object raw = context.get(ATTR_PROJECT_ID);
    long projectId = 0L;
    if (raw instanceof Number num) {
      projectId = num.longValue();
    } else if (raw instanceof String str) {
      try {
        projectId = Long.parseLong(str);
      } catch (NumberFormatException ignored) {
        // non-parseable → no project context
      }
    }
    if (projectId <= 0) {
      // 无项目绑定：直通（上层 AgentTurnExecutor 已对 projectId<=0 做 fail 处理，
      // 能到这里说明 RuntimeContext 传播异常；仍直通让工具自行报错，不吞异常）
      return next.apply(input);
    }
    ProjectContext projectContext = new ProjectContext(projectId, "agent-turn");
    // 在订阅线程经作用域绑定上下文，同步触发内层订阅；run 返回即归还，无需手工配对恢复。
    return Flux.create(sink -> {
      try {
        projectContextScope.run(projectContext, () ->
            next.apply(input).subscribe(
                sink::next,
                sink::error,
                sink::complete));
      } catch (Exception immediate) {
        sink.error(immediate);
      }
    });
  }
}
