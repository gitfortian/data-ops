package io.yak.ops.business.quality.execution;

import io.yak.ops.business.quality.QualityPermissionCode;
import io.yak.ops.business.quality.api.QualityEvidenceQueryApi;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Projects the recorded execution, never the current mutable Monitor / Rules. */
@Component
@ConditionalOnQualityEnabled
@RequiredArgsConstructor
public class QualityEvidenceQueryAdapter implements QualityEvidenceQueryApi {
  private static final int MAX_RULES = 100;
  private final QualityExecutionReader executions;
  private final ActionAuthorization authorization;

  @Override
  public ExecutionEvidence require(String executionNo) {
    authorization.requirePermission(QualityPermissionCode.EXECUTION_READ);
    authorization.requirePermission(QualityPermissionCode.MONITOR_READ);
    if (executionNo == null || !executionNo.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new IllegalArgumentException("质量执行编号无效");
    }
    var execution = executions.require(executionNo);
    var rules = execution.rules() == null ? List.<RuleEvidence>of()
        : execution.rules().stream().limit(MAX_RULES).map(rule -> new RuleEvidence(
            rule.ruleId(), rule.ruleName(), rule.columnName(), String.valueOf(rule.checkResult()),
            rule.metricValue(), rule.expectedValue(), rule.durationMs(), rule.createdAt())).toList();
    return new ExecutionEvidence(execution.executionNo(), execution.monitorName(), execution.tableName(),
        String.valueOf(execution.executionStatus()), String.valueOf(execution.checkResult()),
        execution.totalRules(), execution.passedRules(), execution.failedRules(), execution.errorRules(),
        execution.queuedAt(), execution.finishedAt(), rules,
        execution.rules() != null && execution.rules().size() > MAX_RULES);
  }
}
