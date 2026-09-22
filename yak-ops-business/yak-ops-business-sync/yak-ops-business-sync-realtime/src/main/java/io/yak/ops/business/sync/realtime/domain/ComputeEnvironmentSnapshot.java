package io.yak.ops.business.sync.realtime.domain;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;

/** Immutable runtime target captured for every realtime deployment. */
public record ComputeEnvironmentSnapshot(
    long id,
    String name,
    String engineType,
    String deploymentMode,
    String submitterType,
    RuntimeConfig config,
    int version) {

  public static ComputeEnvironmentSnapshot from(ComputeEnvironment environment) {
    if (environment == null) {
      throw new IllegalArgumentException("运行环境不能为空");
    }
    return new ComputeEnvironmentSnapshot(
        environment.id(),
        environment.name(),
        environment.engineType(),
        environment.deploymentMode(),
        environment.submitterType(),
        environment.config(),
        environment.version());
  }

  public String runtimeRevision() {
    String cdcVersion = config == null ? "unknown" : config.flinkCdcVersion();
    return "flink-cdc-cli-" + cdcVersion + "@env-" + id + "-v" + version;
  }
}
