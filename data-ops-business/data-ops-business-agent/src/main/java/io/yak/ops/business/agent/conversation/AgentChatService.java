package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.repository.AgentTurnRepository;
import io.yak.ops.business.agent.repository.SessionRepository;
import io.yak.ops.business.agent.runtime.AgentRuntime;
import io.yak.ops.business.agent.runtime.TurnSubscription;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 智能分析对话稳定入口：SubmitTurn / ResumeTurn / OpenEventStream / Cancel / Rename / Delete。
 * 顺序（提交侧）：归属校验 -> 单飞检查 -> 输入投影落库(QUEUED) -> 即时唤醒调度器 -> 返回 turnId；
 * HTTP 线程绝不推理。顺序（订阅侧）：轮次归属校验 -> 建立尾随通道 -> 按游标增量补发。
 */
@Slf4j
@ConditionalOnAgentEnabled
@Service
@RequiredArgsConstructor
public class AgentChatService {

  private final AgentSessionOwnerValidator ownerValidator;
  private final AgentStreamCoordinator streamCoordinator;
  private final AgentEventStreamTailer eventStreamTailer;
  private final AgentTurnDispatcher turnDispatcher;
  private final AgentTurnRegistry turnRegistry;
  private final AgentTurnRepository turnRepository;
  private final io.yak.ops.business.agent.repository.AgentTurnEventRepository turnEventRepository;
  private final SessionRepository sessionRepository;
  private final AgentRuntime agentRuntime;
  private final CurrentProject currentProject;

  /** 提交新一轮：验证 + 落库 QUEUED + 立即返回。消息树节点在执行线程创建。 */
  public String submitTurn(String sessionId, String message) {
    long userId = requireUserId();
    long projectId = requireProjectId();
    ownerValidator.ensureOwner(sessionId, userId, projectId, message);
    // check-then-insert 竞态窗口以会话维度 stripe 串行化；跨实例并发属多节点前置事项
    Object stripe = SUBMIT_STRIPES.computeIfAbsent(sessionId, key -> new Object());
    String turnId;
    synchronized (stripe) {
      if (turnRepository.hasActiveTurn(sessionId)) {
        throw new IllegalStateException("该会话已有排队或推理中的轮次，请等待完成或点击停止生成");
      }
      turnId = UUID.randomUUID().toString();
      TurnInput input =
          TurnInput.ofStart(UUID.randomUUID().toString(), UUID.randomUUID().toString(), message);
      turnRepository.insertQueued(turnId, sessionId, userId, projectId, TurnKind.START,
          io.yak.ops.business.agent.repository.support.TurnInputCodec.encode(input));
    }
    turnDispatcher.kick();
    return turnId;
  }

  /** 会话维度的提交串行化 stripes（仅进程内；多节点时升级为分布式互斥）。 */
  private static final java.util.concurrent.ConcurrentHashMap<String, Object> SUBMIT_STRIPES =
      new java.util.concurrent.ConcurrentHashMap<>();

  /** HITL 恢复提交：定位 WAITING_INPUT 轮，校验 feedback toolCallId 匹配反问帧，CAS 回 QUEUED 后续跑。 */
  public String submitResume(String sessionId, List<io.yak.ops.business.agent.domain.ToolFeedback> feedbacks) {
    long userId = requireUserId();
    // resume 的 projectId 校验由会话归属校验覆盖：会话创建时已绑定项目
    ownerValidator.assertOwner(sessionId, userId);
    String turnId =
        turnRepository
            .latestWaitingTurnId(sessionId)
            .orElseThrow(() ->
                new TurnConflictException("当前没有等待应答的反问（可能已被应答）；solution=直接提交新问题"));
    // HITL 防伪造：resume 的 feedback 必须携带该轮反问帧的 toolCallId，否则拒绝恢复，
    // 不允许并发第二反问或伪造 toolCallId 绕过 pending 状态机（DOMAIN 硬规则 7）。
    String expectedToolCallId =
        turnEventRepository
            .latestClarifyToolCallId(turnId)
            .orElseThrow(() ->
                new TurnConflictException("该轮次没有可应答的反问帧；solution=直接提交新问题"));
    boolean matched =
        feedbacks != null
            && feedbacks.stream()
                .anyMatch(f -> f.toolCallId() != null && f.toolCallId().equals(expectedToolCallId));
    if (!matched) {
      throw new TurnConflictException(
          "反馈未匹配待应答的反问（toolCallId 不符）；solution=按反问帧的 toolCallId 应答");
    }
    // P1：恢复必须携带用户对反问的应答——以原轮冻结的 assistant 节点构造 RESUME 版输入，
    // 否则执行器按 START 语义重播原问题、用户答句丢失（DOMAIN 硬规则 7 恢复链）。
    io.yak.ops.business.agent.domain.AgentTurnRecord current =
        turnRepository
            .findByTurnId(turnId)
            .orElseThrow(() -> new TurnConflictException("该轮次已不存在，请刷新会话后重试"));
    io.yak.ops.business.agent.domain.TurnInput original;
    try {
      original =
          io.yak.ops.business.agent.repository.support.TurnInputCodec.decode(current.payloadJson());
    } catch (RuntimeException e) {
      throw new TurnConflictException("该轮次输入投影损坏，无法恢复；solution=直接提交新问题");
    }
    String assistantMessageId = original.assistantMessageId();
    if (assistantMessageId == null || assistantMessageId.isBlank()) {
      throw new TurnConflictException("该轮次缺少可恢复的 assistant 节点；solution=直接提交新问题");
    }
    String resumePayload =
        io.yak.ops.business.agent.repository.support.TurnInputCodec.encode(
            TurnInput.ofResume(assistantMessageId, feedbacks));
    if (!turnRepository.requeueForResume(turnId, resumePayload)) {
      throw new TurnConflictException("该轮次状态已变化（可能已被应答或取消），请刷新会话后重试");
    }
    turnDispatcher.kick();
    return turnId;
  }

  /**
   * 订阅轮次事件流。cursor 语义：header/query 显式携带 Last-Event-ID 时增量补发；
   * 未携带时从当前最新帧起步（晚加入不重放历史，避免已渲染内容重复）。
   */
  public SseEmitter openEventStream(
      String turnId, String lastEventIdHeader, Long cursorParam) {
    long userId = requireUserId();
    AgentTurnRecord record = turnRepository
        .findByTurnId(turnId)
        .orElseThrow(() -> new IllegalArgumentException("轮次不存在或不属于当前用户"));
    if (record.userId() != userId) {
      throw new IllegalArgumentException("轮次不存在或不属于当前用户");
    }
    long cursor = resolveCursor(record, lastEventIdHeader, cursorParam);
    SseEmitter emitter = streamCoordinator.create(() -> {});
    eventStreamTailer.watch(emitter, record, cursor);
    return emitter;
  }

  /**
   * 停止生成：优先取消执行中轮次（dispose + 取消终态收尾），否则取消全部排队轮次。
   * 已挂起等待应答的轮次不在此列——它们没有被取消的执行流。
   */
  public void cancel(String sessionId) {
    long userId = requireUserId();
    ownerValidator.assertOwner(sessionId, userId);
    turnRegistry.cancelBySession(sessionId, () -> {
      int cancelled = turnRepository.cancelQueuedBySession(sessionId);
      log.info("queued turns cancelled: sessionId={}, count={}", sessionId, cancelled);
    });
  }

  /** 重命名会话：仅元数据标题，消息真相不受影响。 */
  public void renameSession(String sessionId, String title) {
    long userId = requireUserId();
    ownerValidator.assertOwner(sessionId, userId);
    sessionRepository.updateTitle(sessionId, title);
  }

  /** 删除会话：归属校验 -> 取消在途轮次与排队 -> StateStore 删除 + 元数据删除。报告独立生命周期保留。 */
  public void deleteSession(String sessionId) {
    long userId = requireUserId();
    ownerValidator.assertOwner(sessionId, userId);
    turnRegistry.cancelBySession(sessionId);
    turnRepository.cancelQueuedBySession(sessionId);
    agentRuntime.deleteSessionData(userId, sessionId);
    sessionRepository.deleteBySessionId(sessionId);
  }

  private long resolveCursor(
      io.yak.ops.business.agent.domain.AgentTurnRecord record,
      String lastEventIdHeader,
      Long cursorParam) {
    String raw = lastEventIdHeader != null && !lastEventIdHeader.isBlank()
        ? lastEventIdHeader.trim()
        : cursorParam != null ? String.valueOf(cursorParam) : null;
    if (raw == null) {
      // 未携带游标 = 晚加入订阅：按轮次状态分叉，避免挂起反问帧对晚订阅者不可见。
      // WAITING_INPUT（HITL 挂起待答）必须从 0 重放，让 CLARIFY_REQUESTED 帧
      // 补投给刷新/重新订阅的客户端（否则前端拿不到选项卡只能文字兜底）。
      // 其余状态维持"最新起步"不重放，避免已渲染内容重复。
      return record.status()
              == io.yak.ops.business.agent.domain.TurnStatus.WAITING_INPUT
          ? 0L
          : Math.max(0L, turnEventRepository.latestEventId(record.turnId()));
    }
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Last-Event-ID 非法：" + raw);
    }
  }

  private static long requireUserId() {
    Long userId = YakSecurityContext.getCurrentUserId();
    if (userId == null) {
      throw new IllegalArgumentException("当前无登录用户上下文");
    }
    return userId;
  }

  private long requireProjectId() {
    return currentProject.requireProjectId();
  }
}
