package io.yak.ops.business.quality.dao;

import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.RuleExecutionWorkspaceRow;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 质量执行与规则执行结果数据访问边界。 */
public interface QualityExecutionDao {
  boolean hasActive(long monitorId);
  long insertExecution(QualityExecutionPO execution);
  boolean markRunning(long id, LocalDateTime startedAt);
  void insertRuleExecution(QualityRuleExecutionPO ruleExecution);
  boolean complete(long id, String result, int passed, int failed, int errors, LocalDateTime finishedAt, long durationMs);
  boolean fail(long id, String errorMessage, LocalDateTime finishedAt, long durationMs);

  long countExecutions(Map<String, Object> params);
  List<QualityExecutionPO> selectExecutions(Map<String, Object> params);
  QualityExecutionPO selectByExecutionNo(String executionNo);
  List<QualityRuleExecutionPO> selectRuleExecutions(long executionId);

  long countExecutionWorkspace(Map<String, Object> params);
  List<QualityExecutionPO> selectExecutionWorkspace(Map<String, Object> params);
  long countRuleExecutionWorkspace(Map<String, Object> params);
  List<RuleExecutionWorkspaceRow> selectRuleExecutionWorkspace(Map<String, Object> params);
}
