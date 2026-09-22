package io.yak.ops.business.agent.conversation.query;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.AgentStepRecord;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.HistoryTurn;
import io.yak.ops.business.agent.domain.HistoryTraceStep;
import io.yak.ops.business.agent.domain.HistoryTurnWithTrace;
import io.yak.ops.business.agent.domain.QueryAuditItem;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.domain.SessionObservability;
import io.yak.ops.business.agent.domain.TurnTraceView;
import io.yak.ops.business.agent.repository.AgentStepRepository;
import io.yak.ops.business.agent.repository.AgentTurnEventRepository;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.QueryLogRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.core.project.CurrentProject;
import java.util.ArrayList;
import java.util.List;
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
    var meta = sessionRepository.findBySessionId(sessionId);
    if (meta.isEmpty() || meta.get().userId() != userId) {
      throw new IllegalArgumentException("会话不存在或不属于当前用户");
    }
    List<HistoryTurn> history = agentRuntime.history(userId, sessionId);
    // 合并失败轮次：StateStore 不记录失败，但 turn 表有终态失败记录
    List<AgentTurnRecord> failed = turnRepository.listFailedBySession(sessionId);
    for (AgentTurnRecord record : failed) {
      String errorContent = record.errorMessage() != null && !record.errorMessage().isBlank()
          ? record.errorMessage()
          : "推理执行失败（" + record.errorCode() + "）";
      history.add(new HistoryTurn("error", errorContent));
    }
    // 从事件日志重建 trace；同时携带轮次 ID 供前端懒加载 trace v2 权威视图（O2）
    List<AgentTurnRecord> completed = turnRepository.listCompletedBySession(sessionId);
    List<List<HistoryTraceStep>> traces = completed.stream()
        .map(t -> eventRepository.reconstructTrace(t.turnId()))
        .toList();
    return enrichWithTrace(history, completed, traces, failed);
  }

  /**
   * 历史配对（数数式对齐为既有行为，O2 增补 turnId 透传）：
   * assistant 轮次 ↔ 终态完成轮次按下标配对；error 轮次 ↔ 失败记录按追加序配对。
   */
  private static List<HistoryTurnWithTrace> enrichWithTrace(
      List<HistoryTurn> history,
      List<AgentTurnRecord> completed,
      List<List<HistoryTraceStep>> traces,
      List<AgentTurnRecord> failed) {
    List<HistoryTurnWithTrace> result = new ArrayList<>();
    int traceIdx = 0;
    int errorIdx = 0;
    for (HistoryTurn turn : history) {
      if ("assistant".equals(turn.role()) && traceIdx < completed.size()) {
        AgentTurnRecord record = completed.get(traceIdx);
        result.add(new HistoryTurnWithTrace(
            turn.role(), turn.content(), record.turnId(), traces.get(traceIdx)));
        traceIdx++;
      } else if ("error".equals(turn.role()) && errorIdx < failed.size()) {
        AgentTurnRecord record = failed.get(errorIdx);
        result.add(new HistoryTurnWithTrace(turn.role(), turn.content(), record.turnId(), List.of()));
        errorIdx++;
      } else {
        result.add(new HistoryTurnWithTrace(turn.role(), turn.content(), null, List.of()));
      }
    }
    return result;
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
    if (turn.userId() != userId) {
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
    var meta = sessionRepository.findBySessionId(sessionId);
    if (meta.isEmpty() || meta.get().userId() != userId) {
      throw new IllegalArgumentException("会话不存在或不属于当前用户");
    }
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
}
