package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.HistoryTraceStep;
import io.yak.ops.business.agent.domain.JournaledTurnEvent;
import java.util.List;
import java.util.Optional;

/** 轮次事件投递日志契约（可重建投影）。append 返回的 eventId 即 SSE 游标。 */
public interface AgentTurnEventRepository {

  long append(String turnId, ChatTurnEvent event);

  /** 游标增量读取：eventId > afterId 升序至多 {@code limit} 帧。 */
  List<JournaledTurnEvent> listAfter(String turnId, long afterId, int limit);

  /** 该轮当前最大投递序号；无事件时为 0。订阅端无游标加入时以此为起点避免重复渲染。 */
  long latestEventId(String turnId);

  /** 从事件日志重建该轮次的 trace 步骤（历史回放用）。 */
  List<HistoryTraceStep> reconstructTrace(String turnId);

  /** 该轮最近一条 CLARIFY_REQUESTED 帧的 toolCallId（HITL 恢复校验用）。 */
  Optional<String> latestClarifyToolCallId(String turnId);

  /** Latest clarification delivery projection; never creates SDK pending state. */
  Optional<ChatTurnEvent> latestClarification(String turnId);
}
