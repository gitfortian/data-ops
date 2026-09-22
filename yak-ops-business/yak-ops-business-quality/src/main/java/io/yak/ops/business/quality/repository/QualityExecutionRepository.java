package io.yak.ops.business.quality.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionSpec;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.common.enums.quality.QualityEnums.CheckResult;
import io.yak.ops.common.enums.quality.QualityEnums.TriggerType;
import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence port for execution lifecycle and immutable execution evidence. */
public interface QualityExecutionRepository {
  boolean hasActiveExecution(long monitorId);
  long insertExecution(String executionNo, Monitor monitor, int totalRules, String operator,
      TriggerType triggerType, LocalDateTime queuedAt);
  boolean markExecutionRunning(long id, LocalDateTime startedAt);
  void insertRuleExecution(RuleExecutionSpec ruleExecution);
  boolean completeExecution(long id, CheckResult result, int passed, int failed, int errors,
      LocalDateTime finishedAt, long durationMs);
  boolean failExecution(long id, String errorMessage, LocalDateTime finishedAt, long durationMs);
  PageData<Execution> pageExecutions(QualityQuery.Execution query);
  Optional<Execution> findExecution(String executionNo);
}
