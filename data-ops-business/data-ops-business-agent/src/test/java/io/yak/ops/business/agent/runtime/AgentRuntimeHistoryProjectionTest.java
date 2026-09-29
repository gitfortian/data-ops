package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agentscope.core.message.AssistantMessage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.yak.ops.business.agent.domain.HistoryTurn;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 历史投影分组测试：中间推理/工具消息不拆泡，每轮仅保留用户问题与最终回答。 */
class AgentRuntimeHistoryProjectionTest {

  private static Msg userMsg(String text) {
    return new UserMessage(text);
  }

  /** 中间推理消息：带工具调用（正文为叙述文本）。 */
  private static Msg assistantWithToolCall(String narration, String callId) {
    return AssistantMessage.builder()
        .content(
            TextBlock.builder().text(narration).build(),
            ToolUseBlock.builder()
                .id(callId)
                .name("list_datasets")
                .input(java.util.Map.of())
                .build())
        .build();
  }

  private static Msg toolResult(String callId, String name, String text) {
    return new ToolResultMessage(callId, name, text);
  }

  private static Msg answerMsg(String text) {
    return AssistantMessage.builder()
        .content(TextBlock.builder().text(text).build())
        .build();
  }

  @Test
  void intermediateStepsCollapseIntoSingleAnswerPerTurn() {
    List<Msg> context =
        List.of(
            userMsg("上个月各区域销售额是多少？"),
            assistantWithToolCall("好的，先确认时间范围，再查询数据集。", "call-1"),
            toolResult("call-1", "list_datasets", "dataset 1/2"),
            answerMsg("好的，当前是2026年8月26日，上个月指2026年7月。"),
            assistantWithToolCall("字段需要用fieldId引用，重新查询。", "call-2"),
            toolResult("call-2", "run_dataset_query", "无数据"),
            answerMsg("没有返回数据，让我先探索字段内容。"),
            assistantWithToolCall("继续探索。", "call-3"),
            toolResult("call-3", "get_dataset_fields", "f1..f100"),
            answerMsg("最终结论：该数据集为通用测试数据，无区域与销售额业务含义。"),
            userMsg("第二个问题"),
            answerMsg("回答二。"));

    List<HistoryTurn> turns = AgentRuntime.projectHistory(context);

    assertEquals(4, turns.size());
    assertEquals("user", turns.get(0).role());
    assertEquals("上个月各区域销售额是多少？", turns.get(0).content());
    assertEquals("assistant", turns.get(1).role());
    assertTrue(
        turns.get(1)
            .content()
            .contains("最终结论：该数据集为通用测试数据，无区域与销售额业务含义。"));
    assertEquals("user", turns.get(2).role());
    assertEquals("第二个问题", turns.get(2).content());
    assertEquals("回答二。", turns.get(3).content());
  }

  @Test
  void emptyAssistantTextsAreDropped() {
    List<Msg> context =
        List.of(userMsg("hi"), answerMsg(""), answerMsg("真正的回答"), userMsg("再见"), answerMsg("拜拜"));
    List<HistoryTurn> turns = AgentRuntime.projectHistory(context);

    assertEquals(4, turns.size());
    assertEquals("真正的回答", turns.get(1).content());
  }

  private static void assertTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("断言失败");
    }
  }
}
