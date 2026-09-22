package io.yak.ops.business.workflow.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Locks the PR 4.3 audit ownership without turning Trigger Ledger into a second audit stream. */
class WorkflowScheduleBackfillAuditContractTest {

  @Test
  void businessDateRerunGatewayIsOwnedByAuditCoordinator() throws IOException {
    String manager = source("backfill/WorkflowBackfillManager.java");
    String coordinator = source("backfill/WorkflowBackfillAuditCoordinator.java");

    assertThat(manager).doesNotContain("implements WorkflowBusinessDateRerunGateway");
    assertThat(coordinator).contains("implements WorkflowBusinessDateRerunGateway");
  }

  @Test
  void schedulerAutomaticLifecycleUsesAuditedScheduleBoundary() throws IOException {
    String handler = source("schedule/trigger/WorkflowScheduleTriggerHandler.java");
    String guard = source("schedule/WorkflowDefinitionScheduleGuard.java");

    assertThat(handler)
        .contains("WorkflowScheduleAuditCoordinator")
        .contains("offlineFromScheduler")
        .contains("scheduleAudit.expire");
    assertThat(guard)
        .contains("WorkflowScheduleAuditCoordinator")
        .contains("onlineFromWorkflow")
        .contains("offlineFromWorkflow");
  }

  @Test
  void triggerLedgerDoesNotCreateItsOwnBusinessAuditOperations() throws IOException {
    String coordinator = source("schedule/trigger/WorkflowScheduleTriggerCoordinator.java");
    String admission = source("schedule/trigger/WorkflowScheduleTriggerAdmission.java");

    assertThat(coordinator)
        .doesNotContain("BusinessAuditService")
        .doesNotContain("AuditOperationRequest");
    assertThat(admission)
        .doesNotContain("BusinessAuditService")
        .doesNotContain("AuditOperationRequest");
  }

  private String source(String relative) throws IOException {
    Path root = Path.of("src/main/java/io/yak/ops/business/workflow");
    if (!Files.isDirectory(root)) {
      root = Path.of(
          "yak-ops-business",
          "yak-ops-business-workflow",
          "src",
          "main",
          "java",
          "io",
          "yak",
          "ops",
          "business",
          "workflow");
    }
    return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
  }
}
