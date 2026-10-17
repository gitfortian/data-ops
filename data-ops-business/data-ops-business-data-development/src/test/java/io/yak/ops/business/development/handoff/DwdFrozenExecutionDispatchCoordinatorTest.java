package io.yak.ops.business.development.handoff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.common.version.VersionDigests;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.spi.task.model.TaskExecutionTrigger;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DwdFrozenExecutionDispatchCoordinatorTest {
  private static final String KEY = "ea56a43b-2373-4f2b-a13e-76e26bd32221";
  private static final String SHA = "a".repeat(64);
  private final CurrentProject project = () -> Optional.of(new ProjectContext(42L, null));
  private final DwdFrozenExecutionAttemptRepository attempts =
      mock(DwdFrozenExecutionAttemptRepository.class);
  private final DevelopmentModelTaskBindingRepository bindings =
      mock(DevelopmentModelTaskBindingRepository.class);
  private final FrozenDwdExecutionCandidateService frozen =
      mock(FrozenDwdExecutionCandidateService.class);
  @SuppressWarnings("unchecked")
  private final ObjectProvider<DwdFrozenExecutionTargetPolicy> policyProvider =
      mock(ObjectProvider.class);
  private final DwdFrozenExecutionTargetPolicy policy =
      mock(DwdFrozenExecutionTargetPolicy.class);
  private final DwdFrozenExecutionReservationCoordinator reservations =
      new DwdFrozenExecutionReservationCoordinator(
          project, attempts, bindings, frozen, policyProvider);
  private final TaskExecutionGateway runtime = mock(TaskExecutionGateway.class);
  private final DwdFrozenExecutionDispatchCoordinator dispatcher =
      new DwdFrozenExecutionDispatchCoordinator(project, reservations, attempts, runtime);
  private final TaskVersionSnapshot snapshot = new TaskVersionSnapshot(
      "development:7", "DWD SQL", "SQL", 2, SHA,
      "{\"taskType\":\"SQL\",\"schemaVersion\":1,\"content\":\"INSERT INTO dwd\"}",
      "{}");

  private DwdFrozenExecutionAttempt receipt(String state) {
    return new DwdFrozenExecutionAttempt(91L, 42, 55, SHA, KEY,
        VersionDigests.sha256Fields("42", "55", SHA, KEY), state, null, null);
  }

  @BeforeEach
  void setup() {
    when(attempts.byKey(KEY)).thenReturn(Optional.empty());
    when(bindings.byId(55)).thenReturn(Optional.of(
        new DevelopmentModelTaskBinding(55L, 42, 50, 90, 3, 901, SHA,
            SHA, SHA, SHA, 7, 92, 2, SHA, 9, "warehouse", "public",
            "dwd_order_detail", "INSERT", SHA, KEY,
            "BINDING_RECORDED_NOT_EXECUTABLE")));
    when(frozen.prepare(55)).thenReturn(
        new FrozenDwdExecutionCandidateService.Candidate(snapshot,
            new FrozenDwdExecutionCandidateService.Preview(
                55, 90, 901, 92, 2, SHA, SHA, "dwd_order_detail",
                false, "FROZEN_REVISION_VERIFIED_EXECUTION_NOT_AUTHORIZED", false)));
    when(policyProvider.getIfAvailable()).thenReturn(policy);
    when(attempts.insert(any())).thenAnswer(invocation -> {
      var inserted = (DwdFrozenExecutionAttempt) invocation.getArgument(0);
      return new DwdFrozenExecutionAttempt(
          91L, inserted.projectId(), inserted.bindingId(), inserted.bindingDigest(),
          inserted.requestKey(), inserted.attemptDigest(), "RESERVED", null, null);
    });
  }

  @Test
  void newRequestReservesBeforeDispatchAndUsesOnlyFrozenPublishedSnapshot() {
    var result = reservations.reserve(55, KEY, true);
    assertTrue(result.newlyReserved());
    assertEquals("RESERVED", result.attempt().state());
    assertEquals(snapshot, result.snapshot());
    verify(attempts).lockBinding(55);
    verify(policy).requireWriteAuthorization(eq(42L), any(), eq(snapshot));
    verify(attempts).insert(any());
    verifyNoInteractions(runtime);
  }

  @Test
  void noApprovedTargetPolicyMeansNoReservationAndNoRuntimeCall() {
    when(policyProvider.getIfAvailable()).thenReturn(null);
    assertThrows(IllegalStateException.class, () -> reservations.reserve(55, KEY, true));
    verify(attempts, never()).insert(any());
    verifyNoInteractions(runtime);
  }

  @Test
  void revokedTargetWritePolicyFailsBeforeInsert() {
    doThrow(new SecurityException("no write")).when(policy)
        .requireWriteAuthorization(eq(42L), any(), eq(snapshot));
    assertThrows(SecurityException.class, () -> reservations.reserve(55, KEY, true));
    verify(attempts, never()).insert(any());
  }

  @Test
  void duplicateSameRequestIsReadOnlyAndDoesNotReturnSnapshotForReexecution() {
    when(attempts.byKey(KEY)).thenReturn(Optional.of(receipt("UNKNOWN")));
    var reservation = reservations.reserve(55, KEY, true);
    assertFalse(reservation.newlyReserved());
    assertNull(reservation.snapshot());
    verifyNoInteractions(frozen, policy, runtime);
    verify(attempts, never()).insert(any());
  }

  @Test
  void sameIdempotencyKeyCannotTargetAnotherBinding() {
    when(attempts.byKey(KEY)).thenReturn(Optional.of(receipt("RUNTIME_ACKNOWLEDGED")));
    assertThrows(IllegalStateException.class, () -> reservations.reserve(56, KEY, true));
    verifyNoInteractions(runtime, frozen);
  }

  @Test
  void invalidConfirmationOrKeyCannotReserveAnything() {
    assertThrows(IllegalArgumentException.class,
        () -> reservations.reserve(55, KEY, false));
    assertThrows(IllegalArgumentException.class,
        () -> reservations.reserve(55, "bad-key", true));
    verifyNoInteractions(attempts, runtime);
  }

  @Test
  void frozenRuntimeSubmissionUsesDurableKeyAndPersistsRealRuntimeId() {
    var reserve = new DwdFrozenExecutionReservationCoordinator.Reservation(
        receipt("RESERVED"), snapshot, true);
    var mockedReservations = mock(DwdFrozenExecutionReservationCoordinator.class);
    when(mockedReservations.reserve(55, KEY, true)).thenReturn(reserve);
    when(attempts.markDispatching(91)).thenReturn(true);
    when(runtime.supports("SQL")).thenReturn(true);
    when(runtime.start(eq(snapshot), eq(TaskExecutionTrigger.MANUAL), eq("dwd:42:" + KEY),
        anyMap())).thenReturn(new TaskExecution("runtime-777", "RUNNING", null, Map.of()));
    when(attempts.byId(91)).thenReturn(Optional.of(new DwdFrozenExecutionAttempt(
        91L, 42, 55, SHA, KEY, receipt("RESERVED").attemptDigest(),
        "RUNTIME_ACKNOWLEDGED", "runtime-777", "RUNNING")));
    var dispatcher = new DwdFrozenExecutionDispatchCoordinator(
        project, mockedReservations, attempts, runtime);
    var result = dispatcher.submit(55, KEY, true);
    assertEquals("runtime-777", result.runtimeExecutionId());
    verify(attempts).markDispatching(91);
    verify(attempts).acknowledge(91, "runtime-777", "RUNNING");
    verify(runtime, times(1)).start(eq(snapshot), eq(TaskExecutionTrigger.MANUAL),
        eq("dwd:42:" + KEY), anyMap());
  }

  @Test
  void unknownAfterRuntimeCallNeverAutomaticallyReplaysRequest() {
    var res = new DwdFrozenExecutionReservationCoordinator.Reservation(
        receipt("RESERVED"), snapshot, true);
    var mockedReservations = mock(DwdFrozenExecutionReservationCoordinator.class);
    when(mockedReservations.reserve(55, KEY, true)).thenReturn(res);
    when(attempts.markDispatching(91)).thenReturn(true);
    when(runtime.supports("SQL")).thenReturn(true);
    when(runtime.start(eq(snapshot), any(), anyString(), anyMap()))
        .thenThrow(new IllegalStateException("Runtime timed out after accepting SQL"));
    when(attempts.byId(91)).thenReturn(Optional.of(receipt("UNKNOWN")));
    var dispatcher = new DwdFrozenExecutionDispatchCoordinator(
        project, mockedReservations, attempts, runtime);
    assertEquals("UNKNOWN", dispatcher.submit(55, KEY, true).state());
    verify(attempts).markUnknown(91);
    when(mockedReservations.reserve(55, KEY, true)).thenReturn(
        new DwdFrozenExecutionReservationCoordinator.Reservation(
            receipt("UNKNOWN"), null, false));
    assertEquals("UNKNOWN", dispatcher.submit(55, KEY, true).state());
    verify(runtime, times(1)).start(any(), any(), anyString(), anyMap());
  }

  @Test
  void concurrentRequestCannotStartIfCasLost() {
    var res = new DwdFrozenExecutionReservationCoordinator.Reservation(
        receipt("RESERVED"), snapshot, true);
    var mockedReservations = mock(DwdFrozenExecutionReservationCoordinator.class);
    when(mockedReservations.reserve(55, KEY, true)).thenReturn(res);
    when(attempts.markDispatching(91)).thenReturn(false);
    when(attempts.byId(91)).thenReturn(Optional.of(receipt("DISPATCHING")));
    var dispatcher = new DwdFrozenExecutionDispatchCoordinator(
        project, mockedReservations, attempts, runtime);
    assertEquals("DISPATCHING", dispatcher.submit(55, KEY, true).state());
    verifyNoInteractions(runtime);
  }
}
