package io.yak.ops.business.sync.realtime.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeReconcileCoordinator;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeReconciler;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeJobReconcilerTest {

  @Mock private RealtimeReconcileCoordinator coordinator;
  @Mock private RealtimeJobStore store;
  private RealtimeReconciler reconciler;

  @BeforeEach
  void setUp() {
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setReconcileLeaseSeconds(30);
    reconciler = new RealtimeReconciler(coordinator, store, properties);
  }

  @Test
  void skipsReconciliationWhenAnotherInstanceOwnsTheLease() {
    when(store.tryAcquireReconcileLease(anyString(), eq(30))).thenReturn(false);

    reconciler.reconcile();

    verify(coordinator, never()).reconcileAll();
  }

  @Test
  void reconcilesWhenThisInstanceOwnsTheLease() {
    when(store.tryAcquireReconcileLease(anyString(), eq(30))).thenReturn(true);

    reconciler.reconcile();

    verify(coordinator).reconcileAll();
  }
}
