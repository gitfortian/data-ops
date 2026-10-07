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

  private static String encode(Object value) {
    try { return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(value); }
    catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
      throw new IllegalStateException("候选编码失败，未发布");
    }
  }

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
      String text;
      if (execution.target() != null && execution.target().purpose() != null) {
        if (execution.suggestion() == null) {
          text = execution.evidence().validateAnswer("尚未生成通过源域校验的候选，请补充业务约束或重试。");
        } else {
          var suggestion = execution.suggestion();
          String refs = suggestion.evidenceRefs().stream().map(id -> "[" + id + "]")
              .collect(java.util.stream.Collectors.joining(" "));
          text = execution.evidence().validateAnswer("候选基于本轮授权证据 " + refs
              + "。请核对业务条件；带入只改变未保存表单，不自动保存、启用或运行。")
              + "\n\n```yak-suggestion\n" + encode(suggestion) + "\n```";
        }
      } else {
        text = execution.evidence().validateAnswer(original.getTextContent()
            .replaceAll("(?s)```yak-(?:suggestion|evidence|facts).*?```", "[未验证材料已移除]"));
      }
      if (QualityTroubleshootingPrompt.appliesTo(execution.target())) {
        text += "\n\n" + QualityTroubleshootingPrompt.NEXT_STEP;
      }
      if (!execution.verifiedFacts().isEmpty()) text += "\n\n```yak-facts\n" + encode(execution.verifiedFacts()) + "\n```";
      text += "\n\n```yak-evidence\n" + encode(execution.evidence().entries()) + "\n```";
      var validated = original.withContent(List.of(TextBlock.builder().text(text).build()));
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
