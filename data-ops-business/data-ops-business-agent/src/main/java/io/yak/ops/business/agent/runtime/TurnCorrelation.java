package io.yak.ops.business.agent.runtime;

/**
 * 会话到当前执行轮次的关联查询。模型调用中间件用它把步骤记账绑定到 turn_id；
 * 由 {@code AgentRuntime} 实现（推理单飞保证同一会话同一时刻至多一个活跃轮次）。
 */
@FunctionalInterface
public interface TurnCorrelation {

  String NO_TURN = null;

  String turnIdOf(String sessionId);
}
