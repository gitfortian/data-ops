package io.yak.ops.business.sync.realtime.observability;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeLog;
import io.yak.ops.business.sync.realtime.engine.FlinkObservabilityClient;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Read-only projection of execution observability from persisted facts and Flink evidence. */
@Component
public class RealtimeObservabilityReader {

  private final RealtimeJobStore store;
  private final RealtimeRuntimeResolver runtimeResolver;
  private final FlinkObservabilityClient flink;

  public RealtimeObservabilityReader(
      RealtimeJobStore store,
      RealtimeRuntimeResolver runtimeResolver,
      FlinkObservabilityClient flink) {
    this.store = store;
    this.runtimeResolver = runtimeResolver;
    this.flink = flink;
  }

  public RealtimeObservabilityView snapshot(long taskId) {
    DeploymentTarget target = requireTarget(taskId, true);
    return flink.snapshot(target.environment(), target.deployment().engineJobId());
  }

  public String submissionLog(long taskId, int tailLines) {
    DeploymentRow deployment = requireTarget(taskId, false).deployment();
    return flink.submissionLog(deployment.idempotencyKey(), tailLines);
  }

  public RuntimeLog runtimeLog(long taskId, int maxExceptions) {
    DeploymentTarget target = requireTarget(taskId, true);
    return flink.runtimeLog(target.environment(), target.deployment().engineJobId(), maxExceptions);
  }

  private DeploymentTarget requireTarget(long taskId, boolean requireJobId) {
    DefinitionRow definition =
        store
            .definition(taskId)
            .orElseThrow(() -> new IllegalArgumentException("实时同步任务不存在：" + taskId));
    DeploymentRow deployment =
        store
            .latestDeployment(taskId)
            .orElseThrow(() -> new IllegalStateException("任务尚无部署记录"));
    if (requireJobId && !StringUtils.hasText(deployment.engineJobId())) {
      throw new IllegalStateException("部署记录尚无 Flink jobId，请先执行状态对账");
    }
    return new DeploymentTarget(
        deployment, runtimeResolver.deployment(definition, deployment));
  }

  private record DeploymentTarget(
      DeploymentRow deployment, ComputeEnvironmentSnapshot environment) {}
}
