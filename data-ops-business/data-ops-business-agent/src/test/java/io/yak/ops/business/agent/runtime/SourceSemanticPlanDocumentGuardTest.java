package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.agentscope.core.state.InMemoryAgentStateStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSemanticPlanDocumentGuardTest {
  private final SourceSemanticPlanDocumentGuard guard = new SourceSemanticPlanDocumentGuard();

  @Test void verifiesExactPlanRevisionAndBlocksDrift(@TempDir Path workspace) throws Exception {
    Files.createDirectory(workspace.resolve("plans"));
    Path file = workspace.resolve("plans/PLAN.md");
    Files.writeString(file, "# Sources reviewed\nNo writes allowed\n");
    var first = guard.readReview(workspace);
    assertEquals(64,first.sha256().length());
    assertEquals(first,guard.verifyApproved(workspace,first.sha256()));
    Files.writeString(file, "# Changed plan\n");
    assertThrows(IllegalStateException.class,() -> guard.verifyApproved(workspace,first.sha256()));
    Files.delete(file);
    assertThrows(IllegalStateException.class,() -> guard.readReview(workspace));
  }

  @Test void refusesSymlinkAndOversizedOrInvalidUtf8(@TempDir Path workspace) throws Exception {
    Path planDir = Files.createDirectory(workspace.resolve("plans"));
    Path outside = Files.writeString(workspace.resolve("other.md"), "# Outside");
    Path file = planDir.resolve("PLAN.md");
    Files.createSymbolicLink(file, outside);
    assertThrows(IllegalStateException.class,() -> guard.readReview(workspace));
    Files.delete(file);
    Files.write(file, new byte[] {(byte)0xc3, (byte)0x28});
    assertThrows(IllegalStateException.class,() -> guard.readReview(workspace));
    Files.write(file,new byte[65537]);
    assertThrows(IllegalStateException.class,() -> guard.readReview(workspace));
    Files.writeString(file,"    ");
    assertThrows(IllegalStateException.class,() -> guard.readReview(workspace));
  }

  @Test void approvalAndResumeNeedTheSameMaterializedFile(@TempDir Path workspace)
      throws Exception {
    var scope = SourceSemanticScopeChunkContractTest.scope();
    var ledger = new SourceSemanticTaskLedger(new InMemoryAgentStateStore());
    var gate = new SourceSemanticPlanApprovalGate(ledger,guard);
    Path planDir = Files.createDirectory(workspace.resolve("plans"));
    Path file = planDir.resolve("PLAN.md");
    Files.writeString(file,"# Safe plan\n",StandardCharsets.UTF_8);
    var review = guard.readReview(workspace);
    var created = ledger.create("task-plan","alice",scope,1,3,review.sha256(),2,4);
    assertThrows(IllegalStateException.class,() -> gate.approve("alice",31,"task-plan",
        "incorrect-source",workspace,review.sha256()));
    Files.delete(file);
    assertThrows(IllegalStateException.class,() -> gate.approve("alice",31,"task-plan",
        scope.fingerprint(),workspace,review.sha256()));
    assertEquals(SourceSemanticTaskState.Status.PLANNED,
        ledger.read("alice",31,"task-plan").status());
    Files.writeString(file,"# Safe plan\n");
    var approved=gate.approve("alice",31,"task-plan",scope.fingerprint(),workspace,review.sha256());
    assertEquals(SourceSemanticTaskState.Status.READY,approved.status());
    ledger.pause("alice",31,"task-plan");
    Files.writeString(file,"# Edited after approval\n");
    assertThrows(IllegalStateException.class,() -> gate.resumeAfterRecheck(
        "alice",31,"task-plan",scope.fingerprint(),workspace,review.sha256()));
    assertEquals(SourceSemanticTaskState.Status.PAUSED,
        ledger.read("alice",31,"task-plan").status());
    Files.writeString(file,"# Safe plan\n");
    assertEquals(SourceSemanticTaskState.Status.READY,
        gate.resumeAfterRecheck("alice",31,"task-plan",
            scope.fingerprint(),workspace,review.sha256()).status());
  }
}
