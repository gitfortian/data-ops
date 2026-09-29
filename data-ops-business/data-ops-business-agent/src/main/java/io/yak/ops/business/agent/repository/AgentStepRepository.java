package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.domain.AgentStepRecord;
import java.util.List;

/** 步骤级执行事实读契约（turn trace 详情与会话级观测矩阵的数据源）。 */
public interface AgentStepRepository {

  /** 轮次内全部步骤（id 升序 = 执行序）。 */
  List<AgentStepRecord> listByTurn(String turnId);

  /** 会话内全部步骤（id 升序；会话级观测矩阵用，idx_agent_step_session 覆盖）。 */
  List<AgentStepRecord> listBySession(String sessionId);
}
