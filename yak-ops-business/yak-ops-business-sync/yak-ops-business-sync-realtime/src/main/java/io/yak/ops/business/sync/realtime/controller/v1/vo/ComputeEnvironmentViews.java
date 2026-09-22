package io.yak.ops.business.sync.realtime.controller.v1.vo;

import java.time.LocalDateTime;
import java.util.List;

public final class ComputeEnvironmentViews {
  private ComputeEnvironmentViews() {}

  public record Environment(
      long id, String name, String engineType, String deploymentMode, String submitterType,
      RealtimeViews.RuntimeConfig config, boolean enabled, boolean defaultEnvironment, int version,
      LocalDateTime createTime, LocalDateTime updateTime, String lastCheckStatus,
      String lastCheckMessage, LocalDateTime lastCheckTime) {}

  public record Diagnosis(
      Long environmentId, String environmentName, String status, boolean ready, String summary,
      String detectedFlinkVersion, String detectedFlinkCdcVersion, String detectedJavaVersion,
      LocalDateTime checkedAt, List<Check> checks) {}
  public record Check(String key, String label, String status, String message) {}
}
