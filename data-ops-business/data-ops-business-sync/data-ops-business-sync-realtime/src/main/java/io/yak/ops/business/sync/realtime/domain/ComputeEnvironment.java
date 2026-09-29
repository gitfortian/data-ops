package io.yak.ops.business.sync.realtime.domain;

import java.time.LocalDateTime;

/** Runtime environment used by Yak Ops to submit realtime jobs to Flink CDC. */
public record ComputeEnvironment(
    long id,
    String name,
    String engineType,
    String deploymentMode,
    String submitterType,
    RuntimeConfig config,
    boolean enabled,
    boolean defaultEnvironment,
    int version,
    LocalDateTime createTime,
    LocalDateTime updateTime,
    String lastCheckStatus,
    String lastCheckMessage,
    LocalDateTime lastCheckTime) {

  public static final String ENGINE_FLINK_CDC = "FLINK_CDC";
  public static final String DEPLOYMENT_REMOTE = "REMOTE";
  public static final String SUBMITTER_LOCAL = "LOCAL";
  public static final String SUBMITTER_SSH = "SSH";

  /** Source-compatible constructor for callers that predate environment diagnostics. */
  public ComputeEnvironment(
      long id,
      String name,
      String engineType,
      String deploymentMode,
      String submitterType,
      RuntimeConfig config,
      boolean enabled,
      boolean defaultEnvironment,
      int version,
      LocalDateTime createTime,
      LocalDateTime updateTime) {
    this(
        id,
        name,
        engineType,
        deploymentMode,
        submitterType,
        config,
        enabled,
        defaultEnvironment,
        version,
        createTime,
        updateTime,
        null,
        null,
        null);
  }

  /**
   * Settings that describe where and how Flink CDC is submitted. Yak Ops operational settings such
   * as timeouts, log retention and reconcile cadence remain application-level settings.
   */
  public record RuntimeConfig(
      String restUrl,
      String flinkHome,
      String flinkCdcHome,
      String javaHome,
      String flinkVersion,
      String flinkCdcVersion,
      SshConfig ssh) {

    /** Source-compatible constructor for local/legacy environments that do not carry SSH config. */
    public RuntimeConfig(
        String restUrl,
        String flinkHome,
        String flinkCdcHome,
        String javaHome,
        String flinkVersion,
        String flinkCdcVersion) {
      this(restUrl, flinkHome, flinkCdcHome, javaHome, flinkVersion, flinkCdcVersion, null);
    }
  }

  /**
   * OpenSSH client settings for remote submission. Private key material is never stored here: the
   * optional identityFile is only a filesystem path on the Yak Ops host. If it is empty, OpenSSH
   * can use the system SSH configuration or ssh-agent.
   */
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
