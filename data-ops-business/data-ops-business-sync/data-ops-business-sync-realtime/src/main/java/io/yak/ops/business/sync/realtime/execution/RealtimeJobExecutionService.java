package io.yak.ops.business.sync.realtime.execution;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeDeleteSafetyChecker;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeReconcileCoordinator;
import org.springframework.stereotype.Service;

/** Stable application entry for realtime execution lifecycle commands. */
@Service("realtimeJobExecutionApplicationService")
public class RealtimeJobExecutionService {

  private final RealtimeExecutionCoordinator executions;
  private final RealtimeExecutionPreparation preparation;
  private final RealtimeReconcileCoordinator reconciliation;
  private final RealtimeDeleteSafetyChecker deleteSafety;

  public RealtimeJobExecutionService(
      RealtimeExecutionCoordinator executions,
      RealtimeExecutionPreparation preparation,
      RealtimeReconcileCoordinator reconciliation,
      RealtimeDeleteSafetyChecker deleteSafety) {
    this.executions = executions;
    this.preparation = preparation;
    this.reconciliation = reconciliation;
    this.deleteSafety = deleteSafety;
  }

  public RealtimeJobView.Deployment start(long id, String idempotencyKey) {
    return executions.start(id, idempotencyKey);
  }

  public void stop(long id) {
    executions.stop(id);
  }

  public RealtimeJobView.Deployment restartExecution(long id, String idempotencyKey) {
    return executions.restartExecution(id, idempotencyKey);
  }

  public RealtimeJobView.Deployment applyPublishedVersion(long id, String idempotencyKey) {
    return executions.applyPublishedVersion(id, idempotencyKey);
  }

  public RealtimeJobView reconcile(long id) {
    return reconciliation.reconcile(id);
  }

  public void assertSafeToDelete(long id) {
    deleteSafety.assertSafeToDelete(id);
  }

  public JsonNode capabilities(long runtimeEnvironmentId) {
    return preparation.capabilities(runtimeEnvironmentId);
  }
}
