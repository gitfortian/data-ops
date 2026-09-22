package io.yak.ops.business.quality.execution;

import io.yak.framework.common.PageData;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Execution;
import io.yak.ops.business.quality.domain.QualityDomain.RuleExecutionWorkspaceItem;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityExecutionReadRepository;
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
}
