package io.yak.ops.business.sync.realtime.reconcile;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically acquires the module lease and reconciles execution state against Flink. */
@Component
public class RealtimeReconciler {

  private static final Logger LOG = LoggerFactory.getLogger(RealtimeReconciler.class);

  private final RealtimeReconcileCoordinator coordinator;
  private final RealtimeJobStore store;
  private final RealtimeSyncProperties properties;
  private final String leaseOwner = UUID.randomUUID().toString();

  public RealtimeReconciler(
      RealtimeReconcileCoordinator coordinator,
      RealtimeJobStore store,
      RealtimeSyncProperties properties) {
    this.coordinator = coordinator;
    this.store = store;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${yak.sync.realtime.reconcile-delay:10000}")
  public void reconcile() {
    try {
      if (!store.tryAcquireReconcileLease(leaseOwner, properties.getReconcileLeaseSeconds())) {
        return;
      }
      coordinator.reconcileAll();
    } catch (RuntimeException exception) {
      LOG.warn("Realtime state reconciliation failed", exception);
    }
  }
}
