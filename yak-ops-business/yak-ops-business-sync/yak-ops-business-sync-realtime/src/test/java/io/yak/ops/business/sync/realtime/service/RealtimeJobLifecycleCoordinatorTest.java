package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
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

class RealtimeJobLifecycleCoordinatorTest {

  private static final long JOB_ID = 7L;
  private static final long DEPLOYMENT_ID = 19L;
  private static final long DEFINITION_VERSION_ID = 31L;
  private static final String FLINK_JOB_ID = "0123456789abcdef0123456789abcdef";

  private RealtimeJobStore store;
  private RealtimeRuntimeIdentityStore identityStore;
  private FlinkJobDiscoveryClient discovery;
  private RealtimeEngineGateway gateway;
  private RealtimeRuntimeResolver runtimeResolver;
  private RealtimeJobLifecycleCoordinator coordinator;
  private ComputeEnvironmentSnapshot environment;

  @BeforeEach
  void setUp() {
    store = mock(RealtimeJobStore.class);
    identityStore = mock(RealtimeRuntimeIdentityStore.class);
    discovery = mock(FlinkJobDiscoveryClient.class);
    gateway = mock(RealtimeEngineGateway.class);
    runtimeResolver = mock(RealtimeRuntimeResolver.class);
    environment = snapshot();
    when(runtimeResolver.deployment(any(), any())).thenReturn(environment);
    PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
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
  void recoversMissingJobIdThenReconcilesRunningFromExecutionEvenIfTaskProjectionIsStale() {
    DefinitionRow definition = definition("STOPPED", "STOPPED");
    DeploymentRow execution =
        execution(null, "RUNNING", "UNKNOWN", "UNKNOWN", LocalDateTime.now());
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition));
    when(store.lockDefinition(JOB_ID)).thenReturn(definition);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(execution));
    when(identityStore.findByDeploymentId(DEPLOYMENT_ID)).thenReturn(Optional.of("yak-rt-test"));
    when(discovery.findJobIds(environment, "yak-rt-test")).thenReturn(List.of(FLINK_JOB_ID));
    when(gateway.status(environment, FLINK_JOB_ID))
        .thenReturn(new RuntimeStatus(FLINK_JOB_ID, RuntimeStatus.State.RUNNING));

    coordinator.reconcile(JOB_ID);

    verify(store)
        .reconcile(JOB_ID, DEPLOYMENT_ID, "UNKNOWN", "UNKNOWN", FLINK_JOB_ID, null);
    verify(store)
        .reconcile(JOB_ID, DEPLOYMENT_ID, "RUNNING", "RUNNING", FLINK_JOB_ID, null);
    verify(store)
        .event(
            JOB_ID,
            DEPLOYMENT_ID,
            "FLINK_JOB_ID_RECOVERED",
            "UNKNOWN",
            "UNKNOWN",
            "已通过 runtime job identity 找回 Flink JobId：" + FLINK_JOB_ID);
  }

  @Test
  void confirmsStoppedFromExecutionIntentEvenIfTaskProjectionStillSaysRunning() {
    DefinitionRow staleTaskProjection = definition("RUNNING", "RUNNING");
    DeploymentRow execution =
        execution(
            null,
            "STOPPED",
            "STOPPING",
            "UNKNOWN",
            LocalDateTime.now().minusMinutes(5));
    when(store.definition(JOB_ID)).thenReturn(Optional.of(staleTaskProjection));
    when(store.lockDefinition(JOB_ID)).thenReturn(staleTaskProjection);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(execution));
    when(identityStore.findByDeploymentId(DEPLOYMENT_ID)).thenReturn(Optional.of("yak-rt-test"));
    when(discovery.findJobIds(environment, "yak-rt-test")).thenReturn(List.of());

    coordinator.reconcile(JOB_ID);

    verify(store).reconcile(JOB_ID, DEPLOYMENT_ID, "STOPPED", "STOPPED", null, null);
    verify(store)
        .event(
            JOB_ID,
            DEPLOYMENT_ID,
            "STOPPED",
            "STOPPING",
            "STOPPED",
            "恢复窗口内未发现匹配的 Flink runtime job，已确认停止");
  }

  @Test
  void keepsRuntimeUnknownInsteadOfPretendingStopped() {
    DefinitionRow definition = definition("RUNNING", "RUNNING");
    DeploymentRow execution =
        execution(FLINK_JOB_ID, "STOPPED", "STOPPING", "STOPPING", LocalDateTime.now());
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition));
    when(store.lockDefinition(JOB_ID)).thenReturn(definition);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(execution));
    when(gateway.status(environment, FLINK_JOB_ID))
        .thenReturn(new RuntimeStatus(FLINK_JOB_ID, RuntimeStatus.State.UNKNOWN));

    coordinator.reconcile(JOB_ID);

    verify(store)
        .reconcile(
            JOB_ID,
            DEPLOYMENT_ID,
            "UNKNOWN",
            "UNKNOWN",
            FLINK_JOB_ID,
            "Flink 当前运行状态未知");
    verify(store, never())
        .reconcile(JOB_ID, DEPLOYMENT_ID, "STOPPED", "STOPPED", FLINK_JOB_ID, null);
  }

  @Test
  void reconcileAllUsesExecutionCandidatesInsteadOfTaskDesiredStateScan() {
    DefinitionRow staleTaskProjection = definition("STOPPED", "STOPPED");
    DeploymentRow execution =
        execution(FLINK_JOB_ID, "RUNNING", "RUNNING", "RUNNING", LocalDateTime.now());
    when(store.reconcileCandidates()).thenReturn(List.of(execution));
    when(store.definition(JOB_ID)).thenReturn(Optional.of(staleTaskProjection));
    when(store.lockDefinition(JOB_ID)).thenReturn(staleTaskProjection);
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(execution));
    when(gateway.status(environment, FLINK_JOB_ID))
        .thenReturn(new RuntimeStatus(FLINK_JOB_ID, RuntimeStatus.State.RUNNING));

    coordinator.reconcileAll();

    verify(store).reconcileCandidates();
    verify(store)
        .reconcile(JOB_ID, DEPLOYMENT_ID, "RUNNING", "RUNNING", FLINK_JOB_ID, null);
  }

  @Test
  void refusesDeleteWhenFlinkJobIsStillActive() {
    DefinitionRow definition = definition("STOPPED", "FAILED");
    DeploymentRow execution =
        execution(FLINK_JOB_ID, "STOPPED", "FAILED", "FAILED", LocalDateTime.now());
    when(store.definition(JOB_ID)).thenReturn(Optional.of(definition));
    when(store.latestDeployment(JOB_ID)).thenReturn(Optional.of(execution));
    when(gateway.status(environment, FLINK_JOB_ID))
        .thenReturn(new RuntimeStatus(FLINK_JOB_ID, RuntimeStatus.State.RUNNING));

    assertThatThrownBy(() -> coordinator.assertSafeToDelete(JOB_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("仍存在活动任务");
  }

  private DefinitionRow definition(String desired, String observed) {
    return new DefinitionRow(
        JOB_ID,
        "test-job",
        null,
        null,
        environment.id(),
        "PUBLISHED",
        desired,
        observed,
        1,
        1,
        "digest",
        null,
        null,
        null);
  }

  private DeploymentRow execution(
      String engineJobId,
      String desiredState,
      String observedState,
      String status,
      LocalDateTime createTime) {
    return new DeploymentRow(
        DEPLOYMENT_ID,
        JOB_ID,
        DEFINITION_VERSION_ID,
        1,
        null,
        "summary",
        "digest",
        "key",
        engineJobId,
        null,
        environment,
        "FLINK_CDC",
        desiredState,
        observedState,
        status,
        "UNKNOWN".equals(observedState),
        null,
        createTime,
        createTime);
  }

  private ComputeEnvironmentSnapshot snapshot() {
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
