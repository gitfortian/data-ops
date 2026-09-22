package io.yak.ops.business.agent.toolset;

/** 系统提示词贡献者：runtime 组装 sysPrompt 时收集并追加各能力域的上下文段落。 */
public interface AgentSystemPromptContributor {

  /**
   * 追加到基础提示词之后的段落；返回 null/空白表示本轮无贡献。
   * 实现必须自我降级：任何内部失败都应返回空串而不是抛出。
   */
  String contribute();
}
