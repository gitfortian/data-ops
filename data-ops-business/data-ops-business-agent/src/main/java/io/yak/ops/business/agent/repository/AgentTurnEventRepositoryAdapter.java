package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentTurnEventMapper;
import io.yak.ops.business.agent.dao.model.AgentTurnEventPO;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.HistoryTraceStep;
import io.yak.ops.business.agent.domain.JournaledTurnEvent;
import io.yak.ops.business.agent.repository.support.TurnEventFrameCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/** 事件投递日志适配。append 与读取均为追加式投影；记录失败只告警不影响执行事实。 */
@Slf4j
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class AgentTurnEventRepositoryAdapter implements AgentTurnEventRepository {

  private final AgentTurnEventMapper mapper;

  @Override
  public long append(String turnId, ChatTurnEvent event) {
    AgentTurnEventPO po = new AgentTurnEventPO();
    po.setTurnId(turnId);
    po.setEventType(event.type().name());
    po.setPayloadJson(TurnEventFrameCodec.encode(event));
    mapper.insert(po);
    return po.getId() == null ? -1L : po.getId();
  }

  @Override
  public List<JournaledTurnEvent> listAfter(String turnId, long afterId, int limit) {
    return mapper.selectList(
            Wrappers.<AgentTurnEventPO>lambdaQuery()
                .eq(AgentTurnEventPO::getTurnId, turnId)
                .gt(AgentTurnEventPO::getId, afterId)
                .orderByAsc(AgentTurnEventPO::getId)
                .last("LIMIT " + Math.max(1, limit)))
        .stream()
        .map(this::toJournaled)
        .toList();
  }

  @Override
  public long latestEventId(String turnId) {
    AgentTurnEventPO tail =
        mapper.selectOne(
            Wrappers.<AgentTurnEventPO>lambdaQuery()
                .eq(AgentTurnEventPO::getTurnId, turnId)
                .orderByDesc(AgentTurnEventPO::getId)
                .last("LIMIT 1"));
    return tail == null ? 0L : tail.getId();
  }

  @Override
  public Optional<String> latestClarifyToolCallId(String turnId) {
    return latestClarification(turnId).map(ChatTurnEvent::toolCallId);
  }

  @Override
  public Optional<ChatTurnEvent> latestClarification(String turnId) {
    AgentTurnEventPO tail =
        mapper.selectOne(
            Wrappers.<AgentTurnEventPO>lambdaQuery()
                .eq(AgentTurnEventPO::getTurnId, turnId)
                .eq(AgentTurnEventPO::getEventType, ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED.name())
                .orderByDesc(AgentTurnEventPO::getId)
                .last("LIMIT 1"));
    if (tail == null) {
      return Optional.empty();
    }
    ChatTurnEvent event =
        TurnEventFrameCodec.decode(tail.getPayloadJson(), tail.getEventType());
    return Optional.of(event);
  }

  @Override
  public List<HistoryTraceStep> reconstructTrace(String turnId) {
    List<AgentTurnEventPO> events = mapper.selectList(
        Wrappers.<AgentTurnEventPO>lambdaQuery()
            .eq(AgentTurnEventPO::getTurnId, turnId)
            .orderByAsc(AgentTurnEventPO::getId));
    return buildTrace(
        events.stream()
            .map(po -> TurnEventFrameCodec.decode(po.getPayloadJson(), po.getEventType()))
            .toList());
  }

  /** 从事件序列重建 trace 步骤：合并连续 THINKING_DELTA，配对 TOOL_CALL/TOOL_RESULT。 */
  static List<HistoryTraceStep> buildTrace(List<ChatTurnEvent> events) {
    List<HistoryTraceStep> steps = new ArrayList<>();
    StringBuilder thinkBuffer = new StringBuilder();
    for (ChatTurnEvent event : events) {
      switch (event.type()) {
        case THINKING_DELTA -> {
          if (event.delta() != null) {
            thinkBuffer.append(event.delta());
          }
        }
        case TOOL_CALL -> {
          if (thinkBuffer.length() > 0) {
            steps.add(new HistoryTraceStep("think", thinkBuffer.toString(), null, null, null));
            thinkBuffer.setLength(0);
          }
          steps.add(new HistoryTraceStep(
              "call", "", event.toolCallId(), event.toolName(), null));
        }
        case TOOL_RESULT -> {
          for (int i = steps.size() - 1; i >= 0; i--) {
            HistoryTraceStep step = steps.get(i);
            if (step.toolCallId() != null && step.toolCallId().equals(event.toolCallId())) {
              steps.set(i, new HistoryTraceStep(
                  "call", step.text(), step.toolCallId(), step.toolName(), event.toolResult()));
              break;
            }
          }
        }
        default -> {}
      }
    }
    if (thinkBuffer.length() > 0) {
      steps.add(new HistoryTraceStep("think", thinkBuffer.toString(), null, null, null));
    }
    return steps;
  }

  private JournaledTurnEvent toJournaled(AgentTurnEventPO po) {
    ChatTurnEvent event = TurnEventFrameCodec.decode(po.getPayloadJson(), po.getEventType());
    return new JournaledTurnEvent(po.getId(), event);
  }
}
