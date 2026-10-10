package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.tool.PlanModeTools;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import io.agentscope.harness.agent.workspace.plan.PlanModeManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks the pinned SDK's permission engine independently of any business tool or production
 * registration. PlanMode's tool whitelist must not be confused with permission ALLOW.
 */
class SourceSemanticPlanPermissionContractTest {

  @Test
  void defaultModeAsksForPlanWriteUnlessExplicitlyAllowed(@TempDir Path root) throws IOException {
    Path isolatedWorkspace = Files.createDirectory(root.resolve("workspace"));
    try (WorkspaceManager workspace = new WorkspaceManager(isolatedWorkspace,
        new LocalFilesystemSpec().project(root).toFilesystem(isolatedWorkspace, null))) {
      PlanModeManager plan = new PlanModeManager(workspace, null);
      PermissionEngine engine = new PermissionEngine(
          PermissionContextState.builder().mode(PermissionMode.DEFAULT).build());

      var write = new PlanModeTools.PlanWriteTool(plan);
      var enter = new PlanModeTools.PlanEnterTool(plan);
      var exit = new PlanModeTools.PlanExitTool(plan);

      // This is an SDK 2.0.3 behavior check, not a request to allow writes in production.
      assertEquals(PermissionBehavior.ASK,
          engine.checkPermission(write, Map.of("content", "# draft")).block().getBehavior());

      engine.addRule(new PermissionRule(PlanModeTools.PLAN_ENTER, null,
          PermissionBehavior.ALLOW, "isolated-plan-only"));
      engine.addRule(new PermissionRule(PlanModeTools.PLAN_WRITE, null,
          PermissionBehavior.ALLOW, "isolated-plan-only"));

      assertEquals(PermissionBehavior.ALLOW,
          engine.checkPermission(enter, Map.of()).block().getBehavior());
      assertEquals(PermissionBehavior.ALLOW,
          engine.checkPermission(write, Map.of("content", "# draft")).block().getBehavior());
      // The separate exit handshake must still require human confirmation.
      assertEquals(PermissionBehavior.ASK,
          engine.checkPermission(exit, Map.of()).block().getBehavior());
    }
  }

  @Test
  void nonInteractiveModeNeverApprovesPlanExit(@TempDir Path root) throws IOException {
    Path isolatedWorkspace = Files.createDirectory(root.resolve("workspace"));
    try (WorkspaceManager workspace = new WorkspaceManager(isolatedWorkspace,
        new LocalFilesystemSpec().project(root).toFilesystem(isolatedWorkspace, null))) {
      PlanModeManager plan = new PlanModeManager(workspace, null);
      PermissionEngine engine = new PermissionEngine(
          PermissionContextState.builder().mode(PermissionMode.DONT_ASK).build());

      assertEquals(PermissionBehavior.DENY,
          engine.checkPermission(new PlanModeTools.PlanExitTool(plan), Map.of())
              .block().getBehavior());
    }
  }
}
