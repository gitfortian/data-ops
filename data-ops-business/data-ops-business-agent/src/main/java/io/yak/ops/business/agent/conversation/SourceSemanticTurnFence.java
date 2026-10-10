package io.yak.ops.business.agent.conversation;

import io.agentscope.core.state.AgentStateStore;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.AgentTurnRecord;
import io.yak.ops.business.agent.domain.TurnInput;
import io.yak.ops.business.agent.runtime.SourceSemanticPlanDocumentGuard;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskLedger;
import io.yak.ops.business.agent.runtime.SourceSemanticTaskState;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Durable pre-model gate on the ORIGINAL executor. A queued turn racing against
 * task cancellation cannot start inference with a missing or revoked reservation.
 * This is NOT a general datasource ACL permission rehydration service.
 */
@Component
@ConditionalOnAgentEnabled
@ConditionalOnProperty(prefix="yak.agent.source-semantic", name="enabled", havingValue="true")
public class SourceSemanticTurnFence {
  private final SourceSemanticTaskLedger ledger;
  private final SourceSemanticPlanDocumentGuard plans = new SourceSemanticPlanDocumentGuard();
  private final Path root;

  public SourceSemanticTurnFence(AgentStateStore store,
      @Value("${yak.agent.source-semantic.workspace-root:}") String sharedWorkspaceRoot) {
    this.ledger = new SourceSemanticTaskLedger(store);
    if (sharedWorkspaceRoot == null || sharedWorkspaceRoot.isBlank()
        || !Path.of(sharedWorkspaceRoot).isAbsolute())
      throw new IllegalStateException("[F039_SHARED_WORKSPACE_ROOT_REQUIRED]");
    this.root = Path.of(sharedWorkspaceRoot).toAbsolutePath().normalize();
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      throw new IllegalStateException("[F039_SHARED_WORKSPACE_UNAVAILABLE]");
  }

  public void requireActive(AgentTurnRecord original, TurnInput input) {
    if (input.sourceTaskId() == null
        || !input.sourceTaskId().matches("[a-f0-9-]{36}")) {
      throw new IllegalStateException("[F039_ORIGINAL_TASK_BINDING_MISSING]");
    }
    SourceSemanticTaskState task = ledger.read(Long.toString(original.userId()),
        original.projectId(), input.sourceTaskId());
    if ((task.status() != SourceSemanticTaskState.Status.RUNNING
            && task.status() != SourceSemanticTaskState.Status.PAUSE_REQUESTED)
        || !Objects.equals(task.activeTurnId(), original.turnId())
        || !Objects.equals(task.sessionId(), original.sessionId())
        || task.sourceManifest() == null
        || task.frozenChunks() == null || task.activeChunkId() == null) {
      throw new IllegalStateException("[F039_ORIGINAL_TURN_REVOKED]");
    }
    Path workspace = root.resolve(Long.toString(original.projectId()))
        .resolve(Long.toString(original.userId())).resolve(input.sourceTaskId());
    plans.verifyApproved(workspace, task.planSha256());
  }
}
