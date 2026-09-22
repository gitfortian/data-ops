package io.yak.ops.business.agent.memory;

/**
 * 记忆提取的模型调用端口（防腐）：memory 包只看字符串进/字符串出，
 * 框架 Model 类型依赖收敛在 runtime 侧实现。阻塞式签名——提取线程为
 * boundedElastic，阻塞无害；失败以 RuntimeException 传播，调用方 best-effort。
 */
public interface MemoryCompletionPort {

  /** 一次非会话式补全（system+user，聚合为纯文本）；实现方不得有会话副作用。 */
  String complete(String systemPrompt, String userPrompt);
}
