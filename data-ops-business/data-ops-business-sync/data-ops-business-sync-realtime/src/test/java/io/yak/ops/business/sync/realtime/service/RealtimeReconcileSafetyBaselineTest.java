package io.yak.ops.business.sync.realtime.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineException;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Regression baseline for reconciliation uncertainty.
 *
 * <p>The reconciler must converge to explicit UNKNOWN / CONFLICT states when external truth cannot
 * be identified safely. It must never guess a Flink JobId or turn runtime uncertainty into a false
 * terminal result.
 */
class RealtimeReconcileSafetyBaselineTest {

  private static final long TASK_ID = 7L;
  private static final long EXECUTION_ID = 19L;
  private static final long DEFINITION_VERSION_ID = 31L;
  private static final String RUNTIME_NAME = "yak-rt-orders-v3-e19";
  private static final String FLINK_JOB_ID = "0123456789abcdef0123456789abcdef";

  private RealtimeJobStore store;
  private RealtimeRuntimeIdentityStore identityStore;
  private FlinkJobDiscoveryClient discovery;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobLifecycleCoordinator coordinator;
  private ComputeEnvironmentSnapshot environment;
  private DefinitionRow task;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    identityStore = mock(RealtimeRuntimeIdentityStore.class);
    discovery = mock(FlinkJobDiscoveryClient.class);
    gateway = mock(RealtimeEngineGateway.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenAnswer(ignored -> new SimpleTransactionStatus());

    environment = environment();
    task = task();
    when(store.definition(TASK_ID)).thenReturn(Optional.of(task));
    when(store.lockDefinition(TASK_ID)).thenReturn(task);
    when(runtimeResolver.deployment(any(), any())).thenReturn(environment);

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setReconcileFailureThreshold(3);
    coordinator =
        new RealtimeJobLifecycleCoordinator(
            store,
            identityStore,
            discovery,
            gateway,
            runtimeResolver,
            new SyncExecutionStateMachine(),
            properties,
            10,
            transactionManager);
  }

  @Test
  void multipleRuntimeIdentityMatchesBecomeConflictInsteadOfGuessingAJobId() {
    DeploymentRow starting = execution(null, "RUNNING", "STARTING", "SUBMITTING", false);
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(starting));
    when(identityStore.findByDeploymentId(EXECUTION_ID)).thenReturn(Optional.of(RUNTIME_NAME));
    when(discovery.findJobIds(environment, RUNTIME_NAME))
        .thenReturn(List.of("job-a", "job-b"));

    coordinator.reconcile(TASK_ID);

    verify(store)
        .reconcile(
            TASK_ID,
            EXECUTION_ID,
            "CONFLICT",
            "UNKNOWN",
            null,
            "runtime job identity 匹配到 2 个 Flink Job，拒绝自动绑定");
    verify(gateway, never()).status(any(), anyString());
    verify(gateway, never()).stop(any(), anyString());
  }

  @Test
  void stoppingExecutionWithMultipleMatchesStaysUnknownInsteadOfEnteringConflict() {
    DeploymentRow stopping = execution(null, "STOPPED", "STOPPING", "STOPPING", false);
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(stopping));
    when(identityStore.findByDeploymentId(EXECUTION_ID)).thenReturn(Optional.of(RUNTIME_NAME));
    when(discovery.findJobIds(environment, RUNTIME_NAME))
        .thenReturn(List.of("job-a", "job-b"));

    coordinator.reconcile(TASK_ID);

    verify(store)
        .reconcile(
            TASK_ID,
            EXECUTION_ID,
            "UNKNOWN",
            "UNKNOWN",
            null,
            "runtime job identity 匹配到 2 个 Flink Job，拒绝自动绑定");
    verify(gateway, never()).status(any(), anyString());
  }

  @Test
  void repeatedEngineFailuresEventuallyMarkRunningExecutionUnknownNotFailed() {
    DeploymentRow running =
        execution(FLINK_JOB_ID, "RUNNING", "RUNNING", "RUNNING", false);
    when(store.reconcileCandidates()).thenReturn(List.of(running));
    when(store.latestDeployment(TASK_ID)).thenReturn(Optional.of(running));
    when(gateway.status(environment, FLINK_JOB_ID))
        .thenThrow(new RealtimeEngineException("REST unavailable", true, null, null));

    coordinator.reconcileAll();
    coordinator.reconcileAll();
    coordinator.reconcileAll();

    verify(gateway, times(3)).status(environment, FLINK_JOB_ID);
    verify(store)
        .reconcile(
            TASK_ID,
            EXECUTION_ID,
            "UNKNOWN",
            "UNKNOWN",
            FLINK_JOB_ID,
            "Flink 状态不可用：REST unavailable");
    verify(store, never()).markTerminalFailure(TASK_ID, EXECUTION_ID, "REST unavailable");
  }

  private DefinitionRow task() {
    return new DefinitionRow(
        TASK_ID,
        "orders-sync",
        null,
        null,
        environment.id(),
        "PUBLISHED",
        "RUNNING",
        "RUNNING",
        3,
        3,
        "c".repeat(64),
        null,
        LocalDateTime.now(),
        LocalDateTime.now());
  }

  private DeploymentRow execution(
      String engineJobId,
      String desiredState,
      String observedState,
      String status,
      boolean uncertain) {
    LocalDateTime now = LocalDateTime.now();
    return new DeploymentRow(
        EXECUTION_ID,
        TASK_ID,
        DEFINITION_VERSION_ID,
        3,
        null,
        "baseline",
        "d".repeat(64),
        "exec-key",
        engineJobId,
        environment.runtimeRevision(),
        environment,
        "FLINK_CDC",
        desiredState,
        observedState,
        status,
        uncertain,
        null,
        now,
        now);
  }

  private static ComputeEnvironmentSnapshot environment() {
    return new ComputeEnvironmentSnapshot(
        3L,
        "test-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_LOCAL,
        new RuntimeConfig(
            "http://127.0.0.1:8081",
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0"),
        2);
  }
}
