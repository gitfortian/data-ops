package io.yak.ops.business.agent.runtime;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.TextBlock;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import java.util.List;
import reactor.core.publisher.Flux;

/** Citation validation before publishing final text and re-saving the official StateStore message. */
final class GovernanceAnswerGuard {
  private GovernanceAnswerGuard() {}

  static Flux<AgentEvent> guard(Flux<AgentEvent> source, AgentExecutionContext execution,
      RuntimeContext context, ReActAgent agent) {
    // Tool/thinking progress stays streamed; final prose is released only after references are known.
    return source.filter(event -> !(event instanceof TextBlockDeltaEvent)).map(event -> {
      if (!(event instanceof AgentResultEvent result) || result.getResult() == null
          || result.getResult().getGenerateReason() == GenerateReason.TOOL_SUSPENDED
          || (execution.target() == null && execution.evidence().entries().isEmpty()
              && !io.yak.ops.business.agent.domain.GovernanceEvidenceLedger
                  .mentionsEvidence(result.getResult().getTextContent()))) return event;
      var original = result.getResult();
      var validated = original.withContent(List.of(TextBlock.builder()
          .text(execution.evidence().validateAnswer(original.getTextContent())).build()));
      var state = agent.getAgentState(context);
      var messages = state.contextMutable();
      boolean replaced = false;
      for (int i = messages.size() - 1; i >= 0; i--) {
        if (messages.get(i).getId().equals(original.getId())) {
          messages.set(i, validated);
          replaced = true;
          break;
        }
      }
      if (!replaced) throw new IllegalStateException("治理结果与会话状态不一致，未发布解读");
      // Same official store and message identity: history and the next turn see the validated text.
      try {
        agent.saveAgentState(context);
      } catch (RuntimeException persistenceFailure) {
        throw new IllegalStateException("治理结果保存失败，请稍后重试");
      }
      return new AgentResultEvent(validated);
    });
  }
}
