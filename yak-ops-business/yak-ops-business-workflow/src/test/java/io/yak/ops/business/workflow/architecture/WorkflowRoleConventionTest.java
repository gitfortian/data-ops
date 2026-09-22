package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.workflow.backfill.WorkflowBackfillAuditCoordinator;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillManager;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillPlanner;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillQuery;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillReconciler;
import io.yak.ops.business.workflow.backfill.WorkflowBackfillTriggerAdapter;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionAuditCoordinator;
import io.yak.ops.business.workflow.definition.WorkflowDefinitionManager;
import io.yak.ops.business.workflow.execution.WorkflowExecutionAuditBridge;
import io.yak.ops.business.workflow.execution.WorkflowExecutionAuditTerminalListener;
import io.yak.ops.business.workflow.execution.WorkflowExecutionControlAuditCoordinator;
import io.yak.ops.business.workflow.execution.WorkflowExecutionManager;
import io.yak.ops.business.workflow.execution.WorkflowExecutionReactivator;
import io.yak.ops.business.workflow.execution.WorkflowLauncher;
import io.yak.ops.business.workflow.execution.WorkflowPublishedVersionRunner;
import io.yak.ops.business.workflow.observability.WorkflowEventStream;
import io.yak.ops.business.workflow.runtime.WorkflowRuntime;
import io.yak.ops.business.workflow.runtime.WorkflowRuntimeRecovery;
import io.yak.ops.business.workflow.schedule.WorkflowDefinitionScheduleGuard;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleAuditCoordinator;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleLifecycle;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleQuery;
import io.yak.ops.business.workflow.schedule.WorkflowScheduleReconciler;
import io.yak.ops.business.workflow.schedule.engine.WorkflowScheduleEngineBridge;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerAdmission;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;
import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class WorkflowRoleConventionTest {

  @Test
  void stableApplicationFacadesRemainServices() {
    for (Class<?> role : List.of(
        WorkflowDefinitionManager.class,
        WorkflowLauncher.class,
        WorkflowExecutionManager.class,
        WorkflowExecutionReactivator.class,
        WorkflowRuntime.class,
        WorkflowBackfillManager.class)) {
      assertThat(role.getAnnotation(Service.class))
          .as("%s is a stable Workflow facade", role.getSimpleName())
          .isNotNull();
    }
  }

  @Test
  void internalRolesRemainExplicitComponents() {
    for (Class<?> role : List.of(
        WorkflowDefinitionAuditCoordinator.class,
        WorkflowExecutionAuditBridge.class,
        WorkflowExecutionAuditTerminalListener.class,
        WorkflowExecutionControlAuditCoordinator.class,
        WorkflowScheduleAuditCoordinator.class,
        WorkflowBackfillAuditCoordinator.class,
        WorkflowPublishedVersionRunner.class,
        WorkflowRuntimeRecovery.class,
        WorkflowEventStream.class,
        WorkflowBackfillPlanner.class,
        WorkflowBackfillQuery.class,
        WorkflowBackfillReconciler.class,
        WorkflowBackfillTriggerAdapter.class,
        WorkflowDefinitionScheduleGuard.class,
        WorkflowScheduleLifecycle.class,
        WorkflowScheduleQuery.class,
        WorkflowScheduleReconciler.class,
        WorkflowScheduleEngineBridge.class,
        WorkflowScheduleTriggerAdmission.class,
        WorkflowScheduleTriggerCoordinator.class,
        WorkflowScheduleTriggerHandler.class)) {
      assertThat(role.getAnnotation(Component.class))
          .as("%s must remain an explicit internal component role", role.getSimpleName())
          .isNotNull();
      assertThat(role.getAnnotation(Service.class))
          .as("%s must not masquerade as a generic Application Service", role.getSimpleName())
          .isNull();
    }
  }
}
