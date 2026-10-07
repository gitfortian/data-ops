package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentTurnMapper;
import io.yak.ops.business.agent.dao.model.AgentTurnPO;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnKind;
import io.yak.ops.business.agent.domain.TurnStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 轮次生命周期持久化适配。全部转移使用带前置状态的条件 UPDATE：
 * 并发写天然单赢家；非法转移影响行数为 0（返回 false），不视为存储异常。
 */
@Repository
@ConditionalOnAgentEnabled
@RequiredArgsConstructor
public class AgentTurnRepositoryAdapter implements AgentTurnRepository {

  /** 启动清障的统一错误码：进程死亡期间执行中的轮次以 INTERRUPTED 诚实收尾。 */
  public static final String ORPHANED_INTERRUPTED = "ORPHANED_INTERRUPTED";

  private final AgentTurnMapper mapper;

  @Override
  public Optional<AgentTurnRecord> latestBySession(String sessionId) {
    return mapper.selectList(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getSessionId, sessionId)
                .orderByDesc(AgentTurnPO::getId)
                .last("LIMIT 1"))
        .stream().findFirst().map(AgentTurnRepositoryAdapter::toRecord);
  }

  @Override
  public void insertQueued(
      String turnId, String sessionId, long userId, long projectId,
      TurnKind kind, String payloadJson) {
    AgentTurnPO po = new AgentTurnPO();
    po.setTurnId(turnId);
    po.setSessionId(sessionId);
    po.setUserId(userId);
    po.setProjectId(projectId);
    po.setKind(kind.name());
    po.setPayloadJson(payloadJson);
    po.setStatus(TurnStatus.QUEUED.name());
    mapper.insert(po);
  }

  @Override
  public Optional<AgentTurnRecord> findByTurnId(String turnId) {
    return Optional.ofNullable(
            mapper.selectOne(
                Wrappers.<AgentTurnPO>lambdaQuery().eq(AgentTurnPO::getTurnId, turnId)))
        .map(AgentTurnRepositoryAdapter::toRecord);
  }

  @Override
  public boolean claimForExecution(String turnId) {
    return transition(turnId, TurnStatus.QUEUED, TurnStatus.RUNNING, null, null, true);
  }

  @Override
  public boolean markWaitingInput(String turnId) {
    return transition(turnId, TurnStatus.RUNNING, TurnStatus.WAITING_INPUT, null, null, false);
  }

  @Override
  public boolean requeueForResume(String turnId, String resumePayloadJson) {
    // P1：恢复必须是「WAITING_INPUT→QUEUED + kind=RESUME + RESUME 版 payload」联合条件更新；
    // 只转状态会让执行器按 START 重播原问题、用户答句丢失。
    return mapper.update(
            null,
            Wrappers.<AgentTurnPO>lambdaUpdate()
                .eq(AgentTurnPO::getTurnId, turnId)
                .eq(AgentTurnPO::getStatus, TurnStatus.WAITING_INPUT.name())
                .set(AgentTurnPO::getStatus, TurnStatus.QUEUED.name())
                .set(AgentTurnPO::getKind, TurnKind.RESUME.name())
                .set(AgentTurnPO::getPayloadJson, resumePayloadJson))
        > 0;
  }

  @Override
  public boolean complete(String turnId) {
    return transition(turnId, TurnStatus.RUNNING, TurnStatus.COMPLETED, null, null, false);
  }

  @Override
  public boolean fail(String turnId, String errorCode, String errorMessage) {
    return transition(
        turnId, TurnStatus.RUNNING, TurnStatus.FAILED, errorCode, truncate(errorMessage), false);
  }

  @Override
  public boolean cancelRunning(String turnId) {
    return transition(turnId, TurnStatus.RUNNING, TurnStatus.CANCELLED, null, null, false);
  }

  @Override
  public boolean cancelQueued(String turnId) {
    return transition(turnId, TurnStatus.QUEUED, TurnStatus.CANCELLED, null, null, false);
  }

  @Override
  public int cancelQueuedBySession(String sessionId) {
    return mapper.update(
        null,
        Wrappers.<AgentTurnPO>lambdaUpdate()
            .eq(AgentTurnPO::getSessionId, sessionId)
            .eq(AgentTurnPO::getStatus, TurnStatus.QUEUED.name())
            .set(AgentTurnPO::getStatus, TurnStatus.CANCELLED.name())
            .set(AgentTurnPO::getEndTime, LocalDateTime.now()));
  }

  @Override
  public int interruptOrphanRunning() {
    return mapper.update(
        null,
        Wrappers.<AgentTurnPO>lambdaUpdate()
            .eq(AgentTurnPO::getStatus, TurnStatus.RUNNING.name())
            .set(AgentTurnPO::getStatus, TurnStatus.INTERRUPTED.name())
            .set(AgentTurnPO::getErrorCode, ORPHANED_INTERRUPTED)
            .set(AgentTurnPO::getErrorMessage, "进程重启导致执行中断，事实已保留，请重新提问")
            .set(AgentTurnPO::getEndTime, LocalDateTime.now()));
  }

  @Override
  public boolean hasActiveTurn(String sessionId) {
    return mapper.selectCount(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getSessionId, sessionId)
                .in(
                    AgentTurnPO::getStatus,
                    List.of(
                        TurnStatus.QUEUED.name(),
                        TurnStatus.RUNNING.name(),
                        // HITL 反问轮同样占用单飞名额：等待应答期间不允许并发开启新轮
                        TurnStatus.WAITING_INPUT.name())))
        > 0;
  }

  @Override
  public Optional<String> latestWaitingTurnId(String sessionId) {
    return mapper.selectList(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getSessionId, sessionId)
                .eq(AgentTurnPO::getStatus, TurnStatus.WAITING_INPUT.name())
                .orderByDesc(AgentTurnPO::getId)
                .last("LIMIT 1"))
        .stream()
        .findFirst()
        .map(AgentTurnPO::getTurnId);
  }

  @Override
  public List<AgentTurnRecord> listQueued(int limit) {
    return mapper.selectList(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getStatus, TurnStatus.QUEUED.name())
                .orderByAsc(AgentTurnPO::getId)
                .last("LIMIT " + Math.max(1, limit)))
        .stream()
        .map(AgentTurnRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public List<AgentTurnRecord> listFailedBySession(String sessionId) {
    return mapper.selectList(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getSessionId, sessionId)
                .in(AgentTurnPO::getStatus,
                    List.of(TurnStatus.FAILED.name(), TurnStatus.INTERRUPTED.name()))
                .orderByAsc(AgentTurnPO::getId))
        .stream()
        .map(AgentTurnRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public List<AgentTurnRecord> listCompletedBySession(String sessionId) {
    return mapper.selectList(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getSessionId, sessionId)
                .eq(AgentTurnPO::getStatus, TurnStatus.COMPLETED.name())
                .orderByAsc(AgentTurnPO::getId))
        .stream()
        .map(AgentTurnRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public List<AgentTurnRecord> listBySession(String sessionId) {
    return mapper.selectList(
            Wrappers.<AgentTurnPO>lambdaQuery()
                .eq(AgentTurnPO::getSessionId, sessionId)
                .orderByAsc(AgentTurnPO::getId))
        .stream()
        .map(AgentTurnRepositoryAdapter::toRecord)
        .toList();
  }

  private boolean transition(
      String turnId,
      TurnStatus from,
      TurnStatus to,
      String errorCode,
      String errorMessage,
      boolean entering) {
    var update =
        Wrappers.<AgentTurnPO>lambdaUpdate()
            .eq(AgentTurnPO::getTurnId, turnId)
            .eq(AgentTurnPO::getStatus, from.name())
            .set(AgentTurnPO::getStatus, to.name());
    if (entering) {
      update.set(AgentTurnPO::getStartTime, LocalDateTime.now());
    }
    if (to.terminal()) {
      update.set(AgentTurnPO::getEndTime, LocalDateTime.now());
    }
    if (errorCode != null) {
      update.set(AgentTurnPO::getErrorCode, errorCode);
      update.set(AgentTurnPO::getErrorMessage, errorMessage);
    }
    return mapper.update(null, update) > 0;
  }

  private static AgentTurnRecord toRecord(AgentTurnPO po) {
    return new AgentTurnRecord(
        po.getTurnId(),
        po.getSessionId(),
        po.getUserId(),
        po.getProjectId() == null ? 0L : po.getProjectId(),
        TurnKind.valueOf(po.getKind()),
        po.getPayloadJson(),
        TurnStatus.valueOf(po.getStatus()),
        po.getErrorCode(),
        po.getErrorMessage(),
        po.getCreateTime(),
        po.getStartTime(),
        po.getEndTime());
  }

  private static String truncate(String message) {
    if (message == null || message.length() <= 1000) {
      return message;
    }
    return message.substring(0, 1000);
  }
}
