package io.yak.ops.business.sync.realtime.execution;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobView;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/** Orchestrates prepare -> claim -> submit -> commit for one new SyncExecution. */
@Component
public class RealtimeExecutionStarter {

  private final RealtimeExecutionPreparation preparation;
  private final RealtimeExecutionReservationManager reservations;
  private final RealtimeExecutionStateManager states;

  public RealtimeExecutionStarter(
      RealtimeExecutionPreparation preparation,
      RealtimeExecutionReservationManager reservations,
      RealtimeExecutionStateManager states) {
    this.preparation = preparation;
    this.reservations = reservations;
    this.states = states;
  }

  RealtimeJobView.Deployment start(long taskId, String requestedKey) {
    String key = reservations.normalizeKey(requestedKey);
    Optional<RealtimeJobView.Deployment> existing = reservations.idempotentView(taskId, key);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    RealtimeExecutionPrepared prepared = preparation.preparePublished(taskId);
    preparation.validate(prepared);
    return startPrepared(taskId, key, prepared, true, RealtimeExecutionIntent.START);
  }

  RealtimeJobView.Deployment startPrepared(
      long taskId,
      String key,
      RealtimeExecutionPrepared prepared,
      boolean requireCurrentPublished,
      RealtimeExecutionIntent intent) {
    Optional<RealtimeJobView.Deployment> existing = reservations.idempotentView(taskId, key);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }

    RealtimeExecutionReservationManager.StartReservation reservation;
    try {
      reservation =
          reservations.reserveStart(
              taskId, key, prepared, requireCurrentPublished, intent);
    } catch (DuplicateKeyException exception) {
      return reservations.recoverDuplicate(taskId, key, exception);
    }

    if (!reservation.created()) {
      return reservations.requireIdempotentView(taskId, key);
    }

    RealtimeEngineGateway.DeployResult result;
    try {
      result = preparation.deploy(prepared, key);
    } catch (RealtimeEngineException exception) {
      states.markStartFailure(taskId, reservation.deploymentId(), exception);
      throw exception;
    }

    return states.completeStart(taskId, reservation.deploymentId(), prepared, result);
  }
}
