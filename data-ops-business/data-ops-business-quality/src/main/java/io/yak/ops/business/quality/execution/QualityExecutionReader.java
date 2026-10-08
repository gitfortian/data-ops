package io.yak.ops.business.quality.execution;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityExecutionReadRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Canonical read-side access to quality execution evidence and workspace projections. */
@Component
@ConditionalOnQualityEnabled
public class QualityExecutionReader {
  private final QualityExecutionReadRepository repository;

  public QualityExecutionReader(QualityExecutionReadRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<Execution> page(QualityQuery.Execution query) {
    return repository.page(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<Execution> page(QualityQuery.ExecutionWorkspace query) {
    return repository.page(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public PageData<RuleExecutionWorkspaceItem> pageRules(QualityQuery.ExecutionWorkspace query) {
    return repository.pageRules(query);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Execution require(String executionNo) {
    return repository.find(executionNo)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Execution requireSummary(String executionNo) {
    return repository.findSummary(executionNo)
        .orElseThrow(() -> new IllegalArgumentException("质量执行记录不存在：" + executionNo));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public List<RuleExecution> rulesForComparison(long executionId) {
    return repository.findRulesBounded(executionId, 21);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Execution requireComparisonSummary(String executionNo) {
    return repository.findComparisonSummary(executionNo)
        .orElseThrow(() -> new IllegalArgumentException("质量比较执行不存在"));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Optional<Execution> findSummary(String executionNo) {
    return repository.findSummary(executionNo);
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Optional<Execution> findLatestForTarget(
      long dataSourceId, String databaseName, String schemaName, String tableName) {
    if (dataSourceId <= 0L) throw new IllegalArgumentException("数据源编号无效");
    if (tableName == null || tableName.isBlank()) throw new IllegalArgumentException("物理表名不能为空");
    return repository.findLatestForTarget(dataSourceId, databaseName, schemaName, tableName);
  }

}
