package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnKind;
import java.util.List;
import java.util.Optional;

/**
 * 推理轮次生命周期持久化契约。truth owner 是 {@code yak_agent_turn}。
 * 全部状态转移实现必须带前置状态条件（乐观并发，天然单赢家）。
 */
public interface AgentTurnRepository {

  void insertQueued(
      String turnId, String sessionId, long userId, long projectId,
      TurnKind kind, String payloadJson);

  Optional<AgentTurnRecord> findByTurnId(String turnId);

  /** Latest submitted turn by persistence order, regardless of outcome. */
  Optional<AgentTurnRecord> latestBySession(String sessionId);

  /** 认领执行：QUEUED -> RUNNING。返回 false 表示已被取消或已被其他 worker 认领。 */
  boolean claimForExecution(String turnId);

  /** HITL 挂起：RUNNING -> WAITING_INPUT。 */
  boolean markWaitingInput(String turnId);

  /**
   * 反问恢复重新入队：WAITING_INPUT -> QUEUED（同一 turnId），同时置 kind=RESUME 并覆写
   * payload 为 RESUME 版输入（含用户对反问的应答）。若只做状态转移而不覆写 kind/payload，
   * 执行器会按 START 语义重播原始问题、用户答句丢失（DOMAIN 硬规则 7 恢复链断裂，P1）。
   */
  boolean requeueForResume(String turnId, String resumePayloadJson);

  /** 正常完成：RUNNING -> COMPLETED。 */
  boolean complete(String turnId);

  /** 失败收尾：RUNNING -> FAILED，携带分类错误码。 */
  boolean fail(String turnId, String errorCode, String errorMessage);

  /** 停止生成：RUNNING -> CANCELLED。 */
  boolean cancelRunning(String turnId);

  /** 取消排队：该会话全部 QUEUED -> CANCELLED，返回影响行数。 */
  int cancelQueuedBySession(String sessionId);

  /** 启动清障：遗留 RUNNING 孤儿 -> INTERRUPTED（诚实终态），返回影响行数。 */
  int interruptOrphanRunning();

  /** 会话是否已有排队或执行中的轮次（提交侧单飞依据）。 */
  boolean hasActiveTurn(String sessionId);

  /** 最近一个待应答轮次（resume 提交定位目标）。 */
  Optional<String> latestWaitingTurnId(String sessionId);

  /** FIFO 待执行队列（id 升序）。 */
  List<AgentTurnRecord> listQueued(int limit);

  /** 会话内终态失败轮次（FAILED/INTERRUPTED，按创建时间升序）。 */
  List<AgentTurnRecord> listFailedBySession(String sessionId);

  /** 会话内已完成轮次（COMPLETED，按创建时间升序），用于历史 trace 重建。 */
  List<AgentTurnRecord> listCompletedBySession(String sessionId);

  /** 会话内全部轮次（id 升序，含全部终态），会话级观测矩阵用。 */
  List<AgentTurnRecord> listBySession(String sessionId);
}
