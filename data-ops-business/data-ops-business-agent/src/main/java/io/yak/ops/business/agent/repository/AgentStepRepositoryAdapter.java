package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentStepMapper;
import io.yak.ops.business.agent.dao.model.AgentStepPO;
import io.yak.ops.business.agent.domain.AgentStepRecord;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 步骤级执行事实读适配：只读不写（写入口唯一收敛在 AgentStepRecorder）。 */
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class AgentStepRepositoryAdapter implements AgentStepRepository {

  private final AgentStepMapper mapper;

  @Override
  public List<AgentStepRecord> listByTurn(String turnId) {
    return mapper.selectList(
            Wrappers.<AgentStepPO>lambdaQuery()
                .eq(AgentStepPO::getTurnId, turnId)
                .orderByAsc(AgentStepPO::getId))
        .stream()
        .map(AgentStepRepositoryAdapter::toRecord)
        .toList();
  }

  @Override
  public List<AgentStepRecord> listBySession(String sessionId) {
    return mapper.selectList(
            Wrappers.<AgentStepPO>lambdaQuery()
                .eq(AgentStepPO::getSessionId, sessionId)
                .orderByAsc(AgentStepPO::getId))
        .stream()
        .map(AgentStepRepositoryAdapter::toRecord)
        .toList();
  }

  private static AgentStepRecord toRecord(AgentStepPO po) {
    return new AgentStepRecord(
        po.getId(),
        po.getTurnId(),
        po.getParentStepId(),
        po.getKind(),
        po.getName(),
        po.getToolCallId(),
        po.getStatus(),
        po.getRequestJson(),
        po.getResponseJson(),
        po.getErrorCode(),
        po.getErrorMessage(),
        po.getStatsJson(),
        po.getStartedAt(),
        po.getEndedAt(),
        po.getAttempt(),
        po.getCreateTime());
  }
}
