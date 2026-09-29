package io.yak.ops.business.sync.realtime.controller.v1.dto;

public final class ComputeEnvironmentRequests {
  private ComputeEnvironmentRequests() {}

  public record SaveRequest(
      String name,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean makeDefault) {}

  public record EnabledRequest(boolean enabled) {}

  public record RuntimeConfig(
      String restUrl,
      String flinkHome,
      String flinkCdcHome,
      String javaHome,
      String flinkVersion,
      String flinkCdcVersion,
      SshConfig ssh) {}

  public record SshConfig(
      String executable,
      String host,
      Integer port,
      String user,
      String identityFile,
      String knownHostsFile,
      Boolean strictHostKeyChecking,
      Integer connectTimeoutSeconds,
      String remoteRestAddress,
      Integer remoteRestPort) {}
}
