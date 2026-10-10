package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.JsonFileAgentStateStore;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import io.agentscope.harness.agent.workspace.plan.PlanModeManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * F-039/#494 offline SDK-only integration contract. No network, real model, real source, or
 * business write. A restored PlanModeContextState must never be confused with a plan document.
 */
class SourceSemanticPlanRecoveryContractTest {

  @Test
  void stateStoreAndWorkspaceReopenMustBeReconciled(@TempDir Path root) throws IOException {
    Path project = Files.createDirectory(root.resolve("project"));
    Path workspaceRoot = Files.createDirectory(root.resolve("task-a"));
    Path stateRoot = Files.createDirectory(root.resolve("state"));
    var context = RuntimeContext.builder().userId("owner").sessionId("task-a").build();
    var state = AgentState.builder().userId("owner").sessionId("task-a").build();
    var store = new JsonFileAgentStateStore(stateRoot);
    String plan = "# Scope A\nsource=orders\n";

    try (WorkspaceManager workspace = workspace(project, workspaceRoot)) {
      var manager = new PlanModeManager(workspace, null);
      manager.enter(state);
      manager.writePlan(context, state, plan);
      store.save("owner", "task-a", "agent_state", state);
    }

    var reopenedStore = new JsonFileAgentStateStore(stateRoot);
    AgentState restored = reopenedStore.get("owner", "task-a", "agent_state", AgentState.class)
        .orElseThrow();
    assertTrue(restored.getPlanModeContext().isPlanActive());
    assertEquals("plans/PLAN.md", restored.getPlanModeContext().getCurrentPlanFile());
    assertTrue(reopenedStore.get("owner", "task-b", "agent_state", AgentState.class).isEmpty());
    assertTrue(reopenedStore.get("other", "task-a", "agent_state", AgentState.class).isEmpty());

    try (WorkspaceManager workspace = workspace(project, workspaceRoot)) {
      String actual = workspace.readManagedWorkspaceFileUtf8(context,
          restored.getPlanModeContext().getCurrentPlanFile());
      assertEquals(plan, actual);
      assertEquals(sha256(plan), sha256(actual));
    }
  }

  @Test
  void restoredPlanPathDoesNotProveFileExists(@TempDir Path root) throws IOException {
    Path workspaceRoot = Files.createDirectory(root.resolve("empty-workspace"));
    Path project = Files.createDirectory(root.resolve("project"));
    var state = AgentState.builder().userId("owner").sessionId("task-a").build();
    state.getPlanModeContext().setPlanActive(true);
    state.getPlanModeContext().setCurrentPlanFile("plans/PLAN.md");
    Path stateRoot = Files.createDirectory(root.resolve("state"));
    new JsonFileAgentStateStore(stateRoot).save("owner", "task-a", "agent_state", state);

    AgentState restored = new JsonFileAgentStateStore(stateRoot)
        .get("owner", "task-a", "agent_state", AgentState.class).orElseThrow();
    assertTrue(restored.getPlanModeContext().isPlanActive());

    try (WorkspaceManager workspace = workspace(project, workspaceRoot)) {
      String content = workspace.readManagedWorkspaceFileUtf8(
          RuntimeContext.builder().userId("owner").sessionId("task-a").build(),
          restored.getPlanModeContext().getCurrentPlanFile());
      assertTrue(content == null || content.isBlank(),
          "A persisted plan path must not be interpreted as plan content");
    }
  }

  @Test
  void aChangedWorkspacePlanDoesNotMatchRecordedDigest(@TempDir Path root)
      throws IOException {
    Path project = Files.createDirectory(root.resolve("project"));
    Path workspaceRoot = Files.createDirectory(root.resolve("task-a"));
    var context = RuntimeContext.builder().userId("owner").sessionId("task-a").build();
    String original = "# Plan approved for orders\n";
    try (WorkspaceManager workspace = workspace(project, workspaceRoot)) {
      var state = AgentState.builder().userId("owner").sessionId("task-a").build();
      var plan = new PlanModeManager(workspace, null);
      plan.enter(state);
      plan.writePlan(context, state, original);
      assertEquals(original, workspace.readManagedWorkspaceFileUtf8(context, "plans/PLAN.md"));
    }
    Path file = findPlan(workspaceRoot);
    Files.writeString(file, "# Changed after review\n", StandardCharsets.UTF_8);
    try (WorkspaceManager workspace = workspace(project, workspaceRoot)) {
      String current = workspace.readManagedWorkspaceFileUtf8(context, "plans/PLAN.md");
      assertNotEquals(sha256(original), sha256(current),
          "A stored plan digest is required before executing an approved revision");
    }
  }

  @Test
  void workspacePlanWriteFailureMustNotBeTreatedAsWritten(@TempDir Path root)
      throws IOException {
    Path project = Files.createDirectory(root.resolve("project"));
    Path workspaceRoot = Files.createDirectory(root.resolve("task-a"));
    // Force the intended plans/ directory path to be a regular file.
    Files.writeString(workspaceRoot.resolve("plans"), "unwritable parent",
        StandardCharsets.UTF_8);
    var context = RuntimeContext.builder().userId("owner").sessionId("task-a").build();
    AgentState state = AgentState.builder().userId("owner").sessionId("task-a").build();

    try (WorkspaceManager workspace = workspace(project, workspaceRoot)) {
      var plan = new PlanModeManager(workspace, null);
      plan.enter(state);
      assertThrows(RuntimeException.class,
          () -> plan.writePlan(context, state, "# May not persist\\n"));
      assertTrue(Files.isRegularFile(workspaceRoot.resolve("plans")));
      assertFalse(Files.exists(workspaceRoot.resolve("plans/PLAN.md")));
      // A set path is not a receipt: the adapter must fail closed after a write error.
      assertEquals("plans/PLAN.md", state.getPlanModeContext().getCurrentPlanFile());
    }
  }

  @Test
  void permissionDecisionUsesConfirmMetadataRatherThanToolResultMessage() {
    var call = ToolUseBlock.builder().id("exit-1").name("plan_exit")
        .input(Map.of("summary", "scope reviewed")).build();
    var allow = new ConfirmResult(true, call);
    var reject = new ConfirmResult(false, call);

    Msg approved = UserMessage.builder()
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(allow))).build();
    Msg denied = UserMessage.builder()
        .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(reject))).build();

    assertTrue(approved.getMetadata().containsKey(Msg.METADATA_CONFIRM_RESULTS));
    assertEquals(Msg.METADATA_CONFIRM_RESULTS, "agentscope_confirm_results");
    assertTrue(allow.isConfirmed());
    assertFalse(reject.isConfirmed());
    assertEquals(call.getId(), allow.getToolCall().getId());
    assertEquals(call.getId(), reject.getToolCall().getId());
    assertNotEquals(approved.getMetadata(), denied.getMetadata());
  }

  private static WorkspaceManager workspace(Path project, Path root) {
    return new WorkspaceManager(root,
        new LocalFilesystemSpec().project(project).toFilesystem(root, null));
  }

  private static Path findPlan(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().equals("PLAN.md"))
          .findFirst().orElseThrow();
    }
  }

  private static String sha256(String content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 must be available", impossible);
    }
  }
}
