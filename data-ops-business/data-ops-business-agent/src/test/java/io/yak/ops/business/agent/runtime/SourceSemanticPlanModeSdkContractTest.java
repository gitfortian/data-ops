package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentState;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import io.agentscope.harness.agent.workspace.plan.PlanModeManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Isolated SDK 2.0.3 experiment for F-039/#494. No production Agent registration or business writes.
 *
 * <p>These assertions cover the SDK plan state and physical workspace contract only. They do not
 * certify permission ASK/ConfirmResult, database StateStore recovery, or runtime total budgets.
 */
class SourceSemanticPlanModeSdkContractTest {

  private final List<WorkspaceManager> managers = new ArrayList<>();

  @AfterEach
  void closeWorkspaces() {
    for (WorkspaceManager manager : managers) {
      manager.close();
    }
  }

  @Test
  void twoTasksDoNotOverwriteEachOthersPlan(@TempDir Path root) throws IOException {
    Path project = Files.createDirectory(root.resolve("project"));
    Path taskA = Files.createDirectory(root.resolve("task-a"));
    Path taskB = Files.createDirectory(root.resolve("task-b"));
    PlanModeManager planA = new PlanModeManager(workspace(project, taskA), null);
    PlanModeManager planB = new PlanModeManager(workspace(project, taskB), null);
    AgentState stateA = AgentState.builder().build();
    AgentState stateB = AgentState.builder().build();

    assertEquals("plans/PLAN.md", planA.enter(stateA));
    assertEquals("plans/PLAN.md", planB.enter(stateB));

    planA.writePlan(RuntimeContext.empty(), stateA, "# Authorized task A\nsource=a\n");
    planB.writePlan(RuntimeContext.empty(), stateB, "# Authorized task B\nsource=b\n");

    String actualA = findPlanContent(taskA);
    String actualB = findPlanContent(taskB);
    assertTrue(actualA.contains("source=a"));
    assertTrue(actualB.contains("source=b"));
    assertNotEquals(actualA, actualB);
    assertTrue(planA.isPlanActive(stateA));
    assertTrue(planB.isPlanActive(stateB));
  }

  @Test
  void exitPreservesPlanFileReferenceButClearsMode(@TempDir Path root) throws IOException {
    Path project = Files.createDirectory(root.resolve("project"));
    Path task = Files.createDirectory(root.resolve("task"));
    PlanModeManager plan = new PlanModeManager(workspace(project, task), null);
    AgentState state = AgentState.builder().build();

    plan.enter(state);
    assertEquals("plans/PLAN.md", plan.writePlan(
        RuntimeContext.empty(), state, "# Reviewed plan\n"));
    assertTrue(plan.isPlanActive(state));

    plan.exit(state);

    assertFalse(plan.isPlanActive(state));
    assertEquals("plans/PLAN.md", state.getPlanModeContext().getCurrentPlanFile());
    assertTrue(findPlanContent(task).contains("# Reviewed plan"));
  }

  private WorkspaceManager workspace(Path project, Path root) {
    WorkspaceManager manager = new WorkspaceManager(
        root, new LocalFilesystemSpec().project(project).toFilesystem(root, null));
    managers.add(manager);
    return manager;
  }

  private static String findPlanContent(Path workspace) throws IOException {
    try (Stream<Path> files = Files.walk(workspace)) {
      Path plan = files.filter(Files::isRegularFile)
          .filter(file -> "PLAN.md".equals(file.getFileName().toString()))
          .findFirst().orElseThrow(() -> new AssertionError("Plan file was not persisted"));
      return Files.readString(plan, StandardCharsets.UTF_8);
    }
  }
}
