package io.yak.ops.business.sync.realtime.reconcile;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.engine.FlinkJobDiscoveryClient;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;
import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway.RuntimeStatus;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import org.springframework.stereotype.Component;

/** Verifies both local execution terminality and external runtime inactivity before metadata deletion. */
@Component
public class RealtimeDeleteSafetyChecker {

  private final RealtimeJobStore store;
  private final RealtimeRuntimeIdentityStore identityStore;
  private final FlinkJobDiscoveryClient discovery;
  private final RealtimeEngineGateway gateway;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final SyncExecutionStateMachine stateMachine;

  public RealtimeDeleteSafetyChecker(
      RealtimeJobStore store,
      RealtimeRuntimeIdentityStore identityStore,
      FlinkJobDiscoveryClient discovery,
      RealtimeEngineGateway gateway,
      RealtimeRuntimeResolver runtimeResolver,
      SyncExecutionStateMachine stateMachine) {
    this.store = store;
    this.identityStore = identityStore;
    this.discovery = discovery;
    this.gateway = gateway;
    this.runtimeResolver = runtimeResolver;
    this.stateMachine = stateMachine;
  }

  public void assertSafeToDelete(long taskId) {
    DefinitionRow definition = requireDefinition(taskId);
    DeploymentRow deployment = store.latestDeployment(taskId).orElse(null);
    if (deployment == null) {
      return;
    }

    stateMachine.requireDefinitionMutable(deployment.execution());
    ComputeEnvironmentSnapshot runtimeEnvironment =
        runtimeResolver.deployment(definition, deployment);
    if (hasText(deployment.engineJobId())) {
      requireInactive(gateway.status(runtimeEnvironment, deployment.engineJobId()));
      return;
    }

    String runtimeName = identityStore.findByDeploymentId(deployment.id()).orElse(null);
    if (!hasText(runtimeName)) {
      return;
    }
    for (String jobId : discovery.findJobIds(runtimeEnvironment, runtimeName)) {
      requireInactive(gateway.status(runtimeEnvironment, jobId));
    }
  }

  private void requireInactive(RuntimeStatus runtime) {
    if (runtime.state() == RuntimeStatus.State.RUNNING) {
      throw new IllegalStateException("Flink 中仍存在活动任务，禁止删除实时同步元数据");
    }
    if (runtime.state() == RuntimeStatus.State.UNKNOWN) {
      throw new IllegalStateException("无法确认 Flink 任务是否已停止，禁止删除实时同步元数据");
    }
  }

  private DefinitionRow requireDefinition(long taskId) {
    return store
        .definition(taskId)
        .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + taskId));
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
