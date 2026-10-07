package io.yak.ops.business.agent.conversation.query;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.AgentStepRecord;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.ChatTurnEvent;
import io.yak.ops.business.agent.domain.HistoryTurn;
import io.yak.ops.business.agent.domain.HistoryTurnWithTrace;
import io.yak.ops.business.agent.domain.QueryAuditItem;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.domain.SessionContinuation;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.domain.TurnStatus;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.SessionObservability;
import io.yak.ops.business.agent.domain.TurnTraceView;
import io.yak.ops.business.agent.repository.AgentStepRepository;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.QueryLogRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.repository.support.TurnInputCodec;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.core.project.CurrentProject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 会话 read model：列表来自元数据投影，历史经窄只读走廊读取 StateStore 证据，
 * 审计来自留痕仓储。三者都不修改任何状态（DEPENDENCIES 第 10 节）。
 */
@ConditionalOnAgentEnabled
@Service
@RequiredArgsConstructor
public class AgentSessionQueryService {

  private final SessionRepository sessionRepository;
  private final QueryLogRepository queryLogRepository;
  private final AgentTurnRepository turnRepository;
  private final AgentTurnEventRepository eventRepository;
  private final AgentStepRepository stepRepository;
  private final AgentRuntime agentRuntime;
  private final TraceViewAssembler traceViewAssembler;
  private final CurrentProject currentProject;

  public List<SessionMeta> listCurrentUserSessions() {
    long userId = requireUserId();
    long projectId = currentProject.requireProjectId();
    return sessionRepository.listByUser(userId, projectId);
  }

  /**
   * 历史回放：先做归属校验，再读 StateStore 只读证据；
   * 合并 turn 表中终态失败轮次（FAILED/INTERRUPTED），保证失败在会话历史中可见；
   * 从事件日志重建 assistant 轮次的 trace（思考 + 工具调用链路）。
   */
  public List<HistoryTurnWithTrace> history(String sessionId) {
    long userId = requireUserId();
    requireSessionOwner(sessionId, userId);
    List<HistoryTurn> history = agentRuntime.history(userId, sessionId);
    long projectId = currentProject.requireProjectId();
    Map<String, Long> references = history.stream()
        .filter(t -> "user".equals(t.role()) && t.turnId() != null)
        .collect(Collectors.groupingBy(HistoryTurn::turnId, Collectors.counting()));
    Map<String, List<AgentTurnRecord>> completed = turnRepository.listCompletedBySession(sessionId).stream()
        .collect(Collectors.groupingBy(AgentTurnRecord::turnId));
    List<HistoryTurnWithTrace> result = new ArrayList<>();
    for (HistoryTurn message : history) {
      List<AgentTurnRecord> candidates = message.turnId() == null ? List.of()
          : completed.getOrDefault(message.turnId(), List.of());
      AgentTurnRecord matched = candidates.size() == 1 ? candidates.get(0) : null;
      if ("assistant".equals(message.role()) && references.getOrDefault(message.turnId(), 0L) == 1L
          && matched != null && matched.status() == TurnStatus.COMPLETED
          && belongsTo(matched, sessionId, userId, projectId)) {
        result.add(new HistoryTurnWithTrace(message.role(), message.content(), matched.turnId(),
            eventRepository.reconstructTrace(matched.turnId())));
      } else {
        // Missing/ambiguous references preserve text; position or content cannot prove identity.
        result.add(new HistoryTurnWithTrace(message.role(), message.content(), null, List.of()));
      }
    }
    // 合并失败轮次：StateStore 不记录失败，但 turn 表有终态失败记录
    List<AgentTurnRecord> failed = turnRepository.listFailedBySession(sessionId);
    for (AgentTurnRecord record : failed) {
      if (!belongsTo(record, sessionId, userId, projectId)) {
        throw new IllegalArgumentException("轮次不属于当前用户及项目");
      }
      String errorContent = record.errorMessage() != null && !record.errorMessage().isBlank()
          ? record.errorMessage()
          : "推理执行失败（" + record.errorCode() + "）";
      result.add(new HistoryTurnWithTrace("error", errorContent, record.turnId(), List.of()));
    }
    return result;
  }

  /** Only persisted input selects the task; unreadable input must never become ordinary chat. */
  public SessionContinuation continuation(String sessionId) {
    long userId = requireUserId();
    requireSessionOwner(sessionId, userId);
    var latest = turnRepository.latestBySession(sessionId);
    if (latest.isEmpty()) {
      return new SessionContinuation(sessionId, null, null, null, null, null);
    }
    AgentTurnRecord turn = latest.get();
    if (turn.userId() != userId || turn.projectId() != currentProject.requireProjectId()
        || !sessionId.equals(turn.sessionId())) {
      throw new IllegalArgumentException("轮次不属于当前用户及项目");
    }
    TurnInput input;
    try {
      input = TurnInputCodec.decode(turn.payloadJson());
      if (input == null || input.assistantMessageId() == null || input.assistantMessageId().isBlank()) {
        throw new IllegalStateException("missing input identity");
      }
    } catch (RuntimeException unreadable) {
      return new SessionContinuation(sessionId, turn.turnId(), turn.status(), null, null,
          "任务上下文无法读取，已暂停继续。请刷新重试或新建会话。");
    }
    SessionContinuation.Clarification clarification = null;
    String reason = null;
    if (turn.status() == TurnStatus.QUEUED || turn.status() == TurnStatus.RUNNING) {
      reason = "该会话仍在排队或推理中，请刷新查看最新结果后继续。";
    } else if (turn.status() == TurnStatus.WAITING_INPUT) {
      try {
        var event = eventRepository.latestClarification(turn.turnId()).orElseThrow();
        if (event.type() != ChatTurnEvent.TurnEventType.CLARIFY_REQUESTED
            || event.toolCallId() == null || event.toolCallId().isBlank()
            || !"request_clarification".equals(event.toolName())
            || event.delta() == null || event.delta().isBlank()) {
          throw new IllegalStateException("missing clarification projection");
        }
        clarification = new SessionContinuation.Clarification(event.toolCallId(), event.toolName(), event.delta());
      } catch (RuntimeException unreadable) {
        reason = "待答问题无法读取，已暂停继续。请刷新重试或新建会话。";
      }
    }
    String draft = null;
    String unavailable = null;
    if (turn.status().terminal()) {
      try {
        List<HistoryTurn> originals = agentRuntime.history(userId, sessionId).stream()
            .filter(t -> "user".equals(t.role()) && turn.turnId().equals(t.turnId())).toList();
        if (turn.kind() == TurnKind.START) {
          draft = input.message();
          if (originals.size() > 1 || (originals.size() == 1
              && !java.util.Objects.equals(draft, originals.get(0).content()))) {
            draft = null;
          }
        } else if (turn.kind() == TurnKind.RESUME && originals.size() == 1) {
          draft = originals.get(0).content();
        }
        if (draft == null || draft.isBlank() || draft.length() > 8000) {
          draft = null;
          unavailable = "原问题缺失、冲突或超过草稿上限，请手工整理新问题。";
        }
      } catch (RuntimeException unreadable) {
        unavailable = "原问题暂时无法读取，请刷新或手工整理新问题。";
      }
    }
    String code = turn.status() == TurnStatus.FAILED ? safeErrorCode(turn.errorCode()) : null;
    return new SessionContinuation(sessionId, turn.turnId(), turn.status(), input.governanceTarget(),
        clarification, reason, code, draft, unavailable);
  }

  private static String safeErrorCode(String code) {
    return code != null && List.of("TIMEOUT", "USER_ERROR", "PROVIDER_ERROR", "GUARD_REJECTED").contains(code)
        ? code : "GENERIC";
  }

  private static boolean belongsTo(AgentTurnRecord turn, String sessionId, long userId, long projectId) {
    return sessionId.equals(turn.sessionId()) && turn.userId() == userId && turn.projectId() == projectId;
  }

  /**
   * 轮次 trace 详情 v2（O2）：归属先于一切（轮次归属在提交时冻结）。
   * 组装器产出树/时间轴/聚合/完整性/渲染投影五视图；只读不写。
   */
  public TurnTraceView turnTrace(String turnId) {
    long userId = requireUserId();
    AgentTurnRecord turn =
        turnRepository.findByTurnId(turnId)
            .orElseThrow(() -> new IllegalArgumentException("轮次不存在"));
    if (turn.userId() != userId || turn.projectId() != currentProject.requireProjectId()) {
      throw new IllegalArgumentException("轮次不存在或不属于当前用户");
    }
    return traceViewAssembler.assembleTurn(turn, stepRepository.listByTurn(turnId));
  }

  /**
   * 会话级观测视图（O2 §7.2）：turns × kinds 矩阵，纯 JOIN 无新采集。
   * 归属先于一切：会话不存在或不属于当前用户即拒绝。
   */
  public SessionObservability sessionObservability(String sessionId) {
    long userId = requireUserId();
    requireSessionOwner(sessionId, userId);
    return traceViewAssembler.assembleSession(
        sessionId, turnRepository.listBySession(sessionId), stepRepository.listBySession(sessionId));
  }

  /** 查询审计分页：仅当前用户的留痕，可选会话/数据集过滤。 */
  public io.yak.framework.common.PageData<QueryAuditItem> auditPage(
      long pageNo, long pageSize, String sessionId, Long datasetId) {
    long userId = requireUserId();
    String normalizedSessionId =
        sessionId == null || sessionId.isBlank() ? null : sessionId.trim();
    return queryLogRepository.pageAudit(userId, normalizedSessionId, datasetId, (int) pageNo, (int) pageSize);
  }

  private static long requireUserId() {
    Long userId = YakSecurityContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalArgumentException("当前无登录用户上下文");
    }
    return userId;
  }

  private void requireSessionOwner(String sessionId, long userId) {
    long projectId = currentProject.requireProjectId();
    var meta = sessionRepository.findBySessionId(sessionId);
    if (meta.isEmpty() || meta.get().userId() != userId || meta.get().projectId() != projectId) {
      throw new IllegalArgumentException("会话不存在或不属于当前用户及项目");
    }
  }
}
