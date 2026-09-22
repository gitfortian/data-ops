package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Stores AuditCarrier correlation without rewriting Workflow runtime truth. */
@Repository
@RequiredArgsConstructor
@DependsOn("workflowFlyway")
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowAuditCorrelationRepositoryAdapter
    implements WorkflowAuditCorrelationRepository {

  private final WorkflowExecutionDao executionDao;

  @Override
  public Optional<String> findCarrierJson(String executionId) {
    if (!StringUtils.hasText(executionId)) return Optional.empty();
    String value = executionDao.selectAuditCarrierJson(executionId.trim());
    return StringUtils.hasText(value) ? Optional.of(value.trim()) : Optional.empty();
  }

  @Override
  public boolean replaceCarrierJson(String executionId, String carrierJson) {
    if (!StringUtils.hasText(executionId)) return false;
    String normalized = StringUtils.hasText(carrierJson) ? carrierJson.trim() : null;
    return executionDao.updateAuditCarrier(executionId.trim(), normalized) == 1;
  }
}
