package io.yak.ops.business.agent.runtime;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.yak.ops.business.agent.memory.MemoryCompletionPort;
import java.util.List;
import reactor.core.publisher.Flux;

/**
 * 记忆提取端口实现（runtime 侧持框架 Model，memory 包只看字符串进出）：
 * 独立补全调用（system+user），聚合流式文本块为纯文本；不进会话状态、无工具。
 * 走与主推理同一模型端点（内部单模型前提，设计稿场景前提）。
 */
public class MemoryCompletionAdapter implements MemoryCompletionPort {

  private final Model model;

  public MemoryCompletionAdapter(Model model) {
    this.model = model;
  }

  @Override
  public String complete(String systemPrompt, String userPrompt) {
    List<Msg> messages = List.of(
        Msg.builderForRole(MsgRole.SYSTEM)
            .content(TextBlock.builder().text(systemPrompt).build())
            .build(),
        Msg.builderForRole(MsgRole.USER)
            .content(TextBlock.builder().text(userPrompt).build())
            .build());
    Flux<ChatResponse> stream = model.stream(messages, List.of(), GenerateOptions.builder().build());
    return stream
        .flatMapIterable(ChatResponse::getContent)
        .ofType(io.agentscope.core.message.TextBlock.class)
        .map(TextBlock::getText)
        .collect(StringBuilder::new, StringBuilder::append)
        .map(StringBuilder::toString)
        .block(java.time.Duration.ofSeconds(60));
  }
}
