package io.yak.ops.business.workflow.repository;

import io.yak.ops.business.workflow.dao.WorkflowExecutionDao;
import io.yak.ops.business.workflow.execution.WorkflowExecutionNotificationReader;
import io.yak.ops.common.bean.po.workflow.WorkflowExecutionPO;
import io.yak.ops.common.bean.po.workflow.WorkflowNodeExecutionPO;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/** Persistence adapter for the narrow Workflow notification read contract. */
@Repository
public class WorkflowExecutionNotificationReaderAdapter
    implements WorkflowExecutionNotificationReader {

  private final WorkflowExecutionDao executionDao;

  public WorkflowExecutionNotificationReaderAdapter(WorkflowExecutionDao executionDao) {
    this.executionDao = executionDao;
  }

  @Override
  public Optional<Snapshot> find(String executionId) {
    if (!StringUtils.hasText(executionId)) return Optional.empty();
    WorkflowExecutionPO execution = executionDao.selectExecution(executionId.trim());
    if (execution == null || execution.getProjectId() == null || execution.getProjectId() <= 0L) {
      return Optional.empty();
    }

    String workflowName = StringUtils.hasText(execution.getWorkflowName())
        ? execution.getWorkflowName().trim()
        : execution.getDefinitionId();
    String error = executionDao.selectNodeExecutions(execution.getId()).stream()
        .map(WorkflowNodeExecutionPO::getErrorMessage)
        .filter(StringUtils::hasText)
        .map(String::trim)
        .findFirst()
        .orElse(null);

    return Optional.of(new Snapshot(
        execution.getProjectId(),
        execution.getId(),
        workflowName,
        execution.getStatus(),
        error));
  }
}
