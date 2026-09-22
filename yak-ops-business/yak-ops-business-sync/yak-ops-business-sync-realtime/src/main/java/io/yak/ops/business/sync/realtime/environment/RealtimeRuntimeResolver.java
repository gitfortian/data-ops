package io.yak.ops.business.sync.realtime.environment;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DefinitionRow;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore.DeploymentRow;
import org.springframework.stereotype.Component;

/** Resolves mutable environment bindings and immutable execution environment snapshots. */
@Component
public class RealtimeRuntimeResolver {

  private final ComputeEnvironmentStore environments;
  private final RealtimeJobStore jobs;

  public RealtimeRuntimeResolver(ComputeEnvironmentStore environments, RealtimeJobStore jobs) {
    this.environments = environments;
    this.jobs = jobs;
  }

  public ComputeEnvironmentSnapshot environment(long environmentId, boolean requireEnabled) {
    ComputeEnvironment environment =
        environments
            .find(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("运行环境不存在：" + environmentId));
    if (requireEnabled && !environment.enabled()) {
      throw new IllegalStateException("运行环境已停用，请切换到已启用的运行环境：" + environment.name());
    }
    return ComputeEnvironmentSnapshot.from(environment);
  }

  public ComputeEnvironmentSnapshot definition(DefinitionRow definition, boolean requireEnabled) {
    return environment(definition.runtimeEnvironmentId(), requireEnabled);
  }

  public ComputeEnvironmentSnapshot deployment(DefinitionRow definition, DeploymentRow deployment) {
    if (deployment.runtimeEnvironment() != null) {
      return deployment.runtimeEnvironment();
    }
    return jobs
        .deploymentEnvironment(deployment.id())
        .orElseThrow(() -> new IllegalStateException("实时同步部署缺少运行环境快照：" + deployment.id()));
  }
}
