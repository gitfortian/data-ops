package io.yak.ops.business.workflow.runtime;

import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository;
import io.yak.ops.business.workflow.repository.WorkflowRuntimeRepository.ProjectExecutionRef;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rebuilds non-terminal workflow runtime state after restoring each persisted Project identity. */
@Component
@ConditionalOnProperty(
    prefix = "yak.database",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WorkflowRuntimeRecovery {
  private static final Logger log = LoggerFactory.getLogger(WorkflowRuntimeRecovery.class);

  private final WorkflowRuntime runtimeService;
  private final WorkflowRuntimeRepository runtimePersistence;
  private final ProjectContextScope projectScope;

  public WorkflowRuntimeRecovery(
      WorkflowRuntime runtimeService,
      WorkflowRuntimeRepository runtimePersistence,
      ProjectContextScope projectScope) {
    this.runtimeService = runtimeService;
    this.runtimePersistence = runtimePersistence;
    this.projectScope = projectScope;
  }

  @Order(10)
  @EventListener(ApplicationReadyEvent.class)
  public void recover() {
    Map<Long, List<ProjectExecutionRef>> byProject = new LinkedHashMap<>();
    for (ProjectExecutionRef candidate : runtimePersistence.findRecoverableExecutionsForDispatch()) {
      byProject.computeIfAbsent(candidate.projectId(), ignored -> new java.util.ArrayList<>())
          .add(candidate);
    }

    for (Map.Entry<Long, List<ProjectExecutionRef>> entry : byProject.entrySet()) {
      long projectId = entry.getKey();
      try {
        projectScope.run(
            new ProjectContext(projectId, null),
            () -> recoverProject(projectId, entry.getValue()));
      } catch (RuntimeException exception) {
        log.error(
            "[workflow] startup recovery failed projectId={}, message={}",
            projectId,
            exception.getMessage(),
            exception);
      }
    }
  }

  private void recoverProject(long projectId, List<ProjectExecutionRef> candidates) {
    // Register executions as active before reconciliation. This does not execute a node by itself;
    // it only guarantees that a recovered SUBMITTED dispatch drains immediately when reconstructed.
    for (ProjectExecutionRef candidate : candidates) {
      try {
        runtimeService.activate(candidate.executionId());
      } catch (RuntimeException exception) {
        log.error(
            "[workflow] pre-recovery activation failed projectId={}, execution={}, message={}",
            projectId,
            candidate.executionId(),
            exception.getMessage(),
            exception);
      }
    }

    int recovered = runtimeService.recoverPersistedExecutions();
    if (recovered > 0) {
      log.info(
          "[workflow] startup recovery completed projectId={}, executions={}",
          projectId,
          recovered);
    }
  }
}
