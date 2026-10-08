package io.yak.ops.business.quality.repository;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import java.util.List;
import java.util.Optional;

/** Canonical read-side repository for quality execution projections. */
public interface QualityExecutionReadRepository {
  PageData<Execution> page(QualityQuery.Execution query);

  PageData<Execution> page(QualityQuery.ExecutionWorkspace query);

  PageData<RuleExecutionWorkspaceItem> pageRules(QualityQuery.ExecutionWorkspace query);

  Optional<Execution> find(String executionNo);

  /** Lightweight execution projection without rule details, intended for status tracking. */
  Optional<Execution> findSummary(String executionNo);

  List<RuleExecution> findRulesBounded(long executionId, int limit);

  Optional<Execution> findLatestForTarget(
      long dataSourceId, String databaseName, String schemaName, String tableName);
}
