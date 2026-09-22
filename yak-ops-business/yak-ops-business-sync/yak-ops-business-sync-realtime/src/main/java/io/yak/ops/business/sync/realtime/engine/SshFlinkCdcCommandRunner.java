package io.yak.ops.business.sync.realtime.engine;

import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/** Executes Flink CDC CLI remotely through the operating system OpenSSH client. */
final class SshFlinkCdcCommandRunner {

  private static final Pattern SSH_USER = Pattern.compile("[A-Za-z0-9._-]+");
  private static final Pattern SSH_HOST = Pattern.compile("[A-Za-z0-9._:%-]+");

  String configurationError(ComputeEnvironmentSnapshot environment) {
    SshConfig ssh = sshConfig(environment);
    RuntimeConfig config = requireConfig(environment);
    if (!StringUtils.hasText(ssh.executable())) {
      return "SSH executable 不能为空";
    }
    if (!StringUtils.hasText(ssh.host()) || !SSH_HOST.matcher(ssh.host()).matches()) {
      return "SSH host 未配置或格式无效";
    }
    if (!StringUtils.hasText(ssh.user()) || !SSH_USER.matcher(ssh.user()).matches()) {
      return "SSH user 未配置或格式无效";
    }
    int port = ssh.port() == null ? 22 : ssh.port();
    if (port < 1 || port > 65535) {
      return "SSH port 必须在 1-65535 之间";
    }
    if (!absoluteUnixPath(config.flinkHome()) || !absoluteUnixPath(config.flinkCdcHome())) {
      return "SSH 模式下 flink-home 和 flink-cdc-home 必须是远端 Linux 绝对路径";
    }
    if (StringUtils.hasText(config.javaHome()) && !absoluteUnixPath(config.javaHome())) {
      return "SSH 模式下 java-home 必须是远端 Linux 绝对路径";
    }
    Integer remoteRestPort = ssh.remoteRestPort();
    if (remoteRestPort != null && (remoteRestPort < 1 || remoteRestPort > 65535)) {
      return "SSH remote-rest-port 必须在 1-65535 之间";
    }
    return null;
  }

  String endpoint(ComputeEnvironmentSnapshot environment) {
    SshConfig ssh = sshConfig(environment);
    if (!StringUtils.hasText(ssh.user()) || !StringUtils.hasText(ssh.host())) {
      return null;
    }
    int port = ssh.port() == null ? 22 : ssh.port();
    return ssh.user() + "@" + ssh.host() + ":" + port;
  }

  void validateReady(ComputeEnvironmentSnapshot environment, URI restUri) {
    String error = configurationError(environment);
    if (error != null) {
      throw failure(error, false, null);
    }
    remoteRestPort(environment, restUri);
    Process process = null;
    try {
      process =
          new ProcessBuilder(sshCommand(environment, remoteProbeCommand(environment, restUri)))
              .redirectErrorStream(true)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .start();
      process.getOutputStream().close();
      Duration timeout = connectTimeout(sshConfig(environment)).plusSeconds(5);
      if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
        destroy(process);
        throw failure("SSH 远端环境探测超时", false, null);
      }
      if (process.exitValue() != 0) {
        throw failure(probeFailureMessage(process.exitValue()), false, null);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      destroy(process);
      throw failure("SSH 远端环境探测被中断", false, exception);
    } catch (IOException exception) {
      destroy(process);
      throw failure("无法启动 OpenSSH 客户端：" + exception.getMessage(), false, exception);
    }
  }

  /** Runs a richer, read-only SSH probe used by the settings diagnostics UI. */
  RemoteProbe probe(ComputeEnvironmentSnapshot environment, URI restUri) {
    String error = configurationError(environment);
    if (error != null) {
      throw failure(error, false, null);
    }
    remoteRestPort(environment, restUri);
    Process process = null;
    try {
      process =
          new ProcessBuilder(sshCommand(environment, remoteDiagnosticCommand(environment, restUri)))
              .redirectErrorStream(true)
              .start();
      process.getOutputStream().close();
      Duration timeout = connectTimeout(sshConfig(environment)).plusSeconds(15);
      if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
        destroy(process);
        throw failure("SSH 运行环境检测超时", false, null);
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      if (process.exitValue() != 0) {
        throw failure(probeFailureMessage(process.exitValue()), false, null);
      }
      return parseRemoteProbe(output);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      destroy(process);
      throw failure("SSH 运行环境检测被中断", false, exception);
    } catch (IOException exception) {
      destroy(process);
      throw failure("无法启动 OpenSSH 客户端：" + exception.getMessage(), false, exception);
    }
  }

  ExecutionResult submit(
      ComputeEnvironmentSnapshot environment,
      String pipelineYaml,
      Path outputLog,
      URI restUri,
      Duration timeout) {
    String error = configurationError(environment);
    if (error != null) {
      throw failure(error, false, null);
    }
    Duration effectiveTimeout = positiveDuration(timeout, Duration.ofSeconds(60));
    Process process = null;
    boolean started = false;
    try {
      ProcessBuilder builder =
          new ProcessBuilder(sshCommand(environment, remoteSubmitCommand(environment, restUri)))
              .redirectErrorStream(true)
              .redirectOutput(outputLog.toFile());
      process = builder.start();
      started = true;
      try (OutputStream stdin = process.getOutputStream()) {
        stdin.write(pipelineYaml.getBytes(StandardCharsets.UTF_8));
        stdin.flush();
      }
      if (!process.waitFor(Math.max(1, effectiveTimeout.toMillis()), TimeUnit.MILLISECONDS)) {
        destroy(process);
        throw failure("SSH Flink CDC 提交超时，远端结果不确定，请通过状态对账确认", true, null);
      }
      int exitCode = process.exitValue();
      return new ExecutionResult(exitCode, exitCode == 255);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      destroy(process);
      throw failure("SSH Flink CDC 提交被中断，远端结果不确定", started, exception);
    } catch (IOException exception) {
      destroy(process);
      throw failure(
          started
              ? "SSH 连接在提交过程中异常，远端结果不确定"
              : "无法启动 OpenSSH 客户端：" + exception.getMessage(),
          started,
          exception);
    }
  }

  private List<String> sshCommand(
      ComputeEnvironmentSnapshot environment, String remoteCommand) {
    SshConfig ssh = sshConfig(environment);
    List<String> command = new ArrayList<>();
    command.add(ssh.executable());
    command.add("-o");
    command.add("BatchMode=yes");
    command.add("-o");
    command.add("ConnectionAttempts=1");
    command.add("-o");
    command.add("ConnectTimeout=" + seconds(ssh));
    command.add("-o");
    command.add("ServerAliveInterval=15");
    command.add("-o");
    command.add("ServerAliveCountMax=2");
    command.add("-o");
    command.add("LogLevel=ERROR");
    command.add("-o");
    command.add(
        "StrictHostKeyChecking=" + (strictHostKeyChecking(ssh) ? "yes" : "accept-new"));
    if (StringUtils.hasText(ssh.knownHostsFile())) {
      command.add("-o");
      command.add("UserKnownHostsFile=" + ssh.knownHostsFile());
    }
    if (StringUtils.hasText(ssh.identityFile())) {
      command.add("-i");
      command.add(ssh.identityFile());
    }
    command.add("-p");
    command.add(Integer.toString(ssh.port() == null ? 22 : ssh.port()));
    command.add(ssh.user() + "@" + ssh.host());
    command.add(remoteCommand);
    return command;
  }

  private String remoteProbeCommand(ComputeEnvironmentSnapshot environment, URI restUri) {
    RuntimeConfig config = requireConfig(environment);
    String cdc = remoteCdcCli(environment);
    StringBuilder command = new StringBuilder("set -eu; ");
    command.append("test -x ").append(shellQuote(cdc)).append(" || exit 41; ");
    command.append("test -d ").append(shellQuote(config.flinkHome())).append(" || exit 42; ");
    command.append("command -v mktemp >/dev/null 2>&1 || exit 43; ");
    if (StringUtils.hasText(config.javaHome())) {
      command
          .append("test -x ")
          .append(shellQuote(config.javaHome() + "/bin/java"))
          .append(" || exit 44; ");
    }
    command.append("test -n ").append(shellQuote(remoteRestAddress(environment, restUri))).append("; ");
    command.append("echo YAK_REALTIME_SSH_READY");
    return command.toString();
  }

  private String remoteDiagnosticCommand(ComputeEnvironmentSnapshot environment, URI restUri) {
    RuntimeConfig config = requireConfig(environment);
    String cdc = remoteCdcCli(environment);
    String flink = config.flinkHome().replaceAll("/+$", "") + "/bin/flink";
    StringBuilder command = new StringBuilder("set +e; ");
    command.append("printf 'YAK_SSH=1\\n'; ");

    command.append("if test -x ").append(shellQuote(cdc)).append("; then ");
    command.append("printf 'YAK_CDC_EXEC=1\\n'; ");
    command.append("v=$(").append(shellQuote(cdc)).append(" --version 2>&1); rc=$?; ");
    command.append("v=$(printf '%s\\n' \"$v\" | head -n 1); ");
    command.append("printf 'YAK_CDC_VERSION_OK=%s\\n' \"$([ $rc -eq 0 ] && echo 1 || echo 0)\"; ");
    command.append("printf 'YAK_CDC_VERSION=%s\\n' \"$v\"; ");
    command.append("else printf 'YAK_CDC_EXEC=0\\n'; fi; ");

    command.append("if test -x ").append(shellQuote(flink)).append("; then ");
    command.append("printf 'YAK_FLINK_EXEC=1\\n'; ");
    command.append("v=$(").append(shellQuote(flink)).append(" --version 2>&1); rc=$?; ");
    command.append("v=$(printf '%s\\n' \"$v\" | head -n 1); ");
    command.append("printf 'YAK_FLINK_VERSION_OK=%s\\n' \"$([ $rc -eq 0 ] && echo 1 || echo 0)\"; ");
    command.append("printf 'YAK_FLINK_VERSION=%s\\n' \"$v\"; ");
    command.append("else printf 'YAK_FLINK_EXEC=0\\n'; fi; ");

    if (StringUtils.hasText(config.javaHome())) {
      String java = config.javaHome().replaceAll("/+$", "") + "/bin/java";
      command.append("if test -x ").append(shellQuote(java)).append("; then ");
      command.append("printf 'YAK_JAVA_EXEC=1\\n'; ");
      command.append("v=$(").append(shellQuote(java)).append(" -version 2>&1); rc=$?; ");
    } else {
      command.append("if command -v java >/dev/null 2>&1; then ");
      command.append("printf 'YAK_JAVA_EXEC=1\\n'; ");
      command.append("v=$(java -version 2>&1); rc=$?; ");
    }
    command.append("v=$(printf '%s\\n' \"$v\" | head -n 1); ");
    command.append("printf 'YAK_JAVA_VERSION_OK=%s\\n' \"$([ $rc -eq 0 ] && echo 1 || echo 0)\"; ");
    command.append("printf 'YAK_JAVA_VERSION=%s\\n' \"$v\"; ");
    command.append("else printf 'YAK_JAVA_EXEC=0\\n'; fi; ");

    command.append("if command -v mktemp >/dev/null 2>&1; then ");
    command.append("tmp=$(mktemp \"${TMPDIR:-/tmp}/yak-ops-probe.XXXXXX\" 2>/dev/null); ");
    command.append("if test -n \"$tmp\" && test -f \"$tmp\"; then rm -f \"$tmp\"; printf 'YAK_TEMP=1\\n'; ");
    command.append("else printf 'YAK_TEMP=0\\n'; fi; else printf 'YAK_TEMP=0\\n'; fi; ");

    command
        .append("printf 'YAK_REMOTE_REST=%s:%s\\n' ")
        .append(shellQuote(remoteRestAddress(environment, restUri)))
        .append(" ")
        .append(shellQuote(Integer.toString(remoteRestPort(environment, restUri))))
        .append("; exit 0");
    return command.toString();
  }

  private RemoteProbe parseRemoteProbe(String output) {
    Map<String, String> values = new HashMap<>();
    if (output != null) {
      output.lines()
          .filter(line -> line.startsWith("YAK_"))
          .forEach(
              line -> {
                int separator = line.indexOf('=');
                if (separator > 0) {
                  values.put(line.substring(0, separator), line.substring(separator + 1).trim());
                }
              });
    }
    if (!"1".equals(values.get("YAK_SSH"))) {
      throw failure("SSH 已连接，但远端检测命令未返回预期标记", false, null);
    }
    return new RemoteProbe(
        flag(values, "YAK_CDC_EXEC"),
        flag(values, "YAK_CDC_VERSION_OK"),
        values.get("YAK_CDC_VERSION"),
        flag(values, "YAK_FLINK_EXEC"),
        flag(values, "YAK_FLINK_VERSION_OK"),
        values.get("YAK_FLINK_VERSION"),
        flag(values, "YAK_JAVA_EXEC"),
        flag(values, "YAK_JAVA_VERSION_OK"),
        values.get("YAK_JAVA_VERSION"),
        flag(values, "YAK_TEMP"));
  }

  private boolean flag(Map<String, String> values, String key) {
    return "1".equals(values.get(key));
  }

  private String remoteSubmitCommand(ComputeEnvironmentSnapshot environment, URI restUri) {
    RuntimeConfig config = requireConfig(environment);
    StringBuilder command = new StringBuilder();
    command.append("set -eu; umask 077; ");
    command.append("tmp=$(mktemp \"${TMPDIR:-/tmp}/yak-ops-cdc.XXXXXX.yaml\"); ");
    command.append("cleanup(){ rm -f \"$tmp\"; }; trap cleanup 0; ");
    command.append("trap 'exit 129' HUP; trap 'exit 130' INT; trap 'exit 143' TERM; ");
    command.append("cat > \"$tmp\"; ");
    command.append("export FLINK_HOME=").append(shellQuote(config.flinkHome())).append("; ");
    if (StringUtils.hasText(config.javaHome())) {
      command.append("export JAVA_HOME=").append(shellQuote(config.javaHome())).append("; ");
    }
    command.append(shellQuote(remoteCdcCli(environment))).append(" \"$tmp\"");
    command.append(" --flink-home ").append(shellQuote(config.flinkHome()));
    command.append(" --target remote");
    command.append(" ").append(shellQuote("-Drest.address=" + remoteRestAddress(environment, restUri)));
    command.append(" ").append(shellQuote("-Drest.port=" + remoteRestPort(environment, restUri)));
    return command.toString();
  }

  private String remoteCdcCli(ComputeEnvironmentSnapshot environment) {
    String home = requireConfig(environment).flinkCdcHome().replaceAll("/+$", "");
    return home + "/bin/flink-cdc.sh";
  }

  private String remoteRestAddress(ComputeEnvironmentSnapshot environment, URI restUri) {
    SshConfig ssh = sshConfig(environment);
    String value =
        StringUtils.hasText(ssh.remoteRestAddress()) ? ssh.remoteRestAddress().trim() : restUri.getHost();
    if (!StringUtils.hasText(value)) {
      throw new IllegalArgumentException("SSH remote REST address 不能为空");
    }
    return value;
  }

  private int remoteRestPort(ComputeEnvironmentSnapshot environment, URI restUri) {
    Integer configured = sshConfig(environment).remoteRestPort();
    if (configured != null) {
      if (configured < 1 || configured > 65535) {
        throw new IllegalArgumentException("ssh.remote-rest-port 必须在 1-65535 之间");
      }
      return configured;
    }
    if (restUri.getPort() > 0) {
      return restUri.getPort();
    }
    return "https".equalsIgnoreCase(restUri.getScheme()) ? 443 : 80;
  }

  private String probeFailureMessage(int exitCode) {
    return switch (exitCode) {
      case 41 -> "SSH 已连接，但远端 Flink CDC CLI 不存在或不可执行";
      case 42 -> "SSH 已连接，但远端 Flink Home 不存在";
      case 43 -> "SSH 已连接，但远端缺少 mktemp 命令";
      case 44 -> "SSH 已连接，但远端 JAVA_HOME/bin/java 不存在或不可执行";
      case 255 -> "SSH 连接或认证失败，请检查 host key、用户和密钥配置";
      default -> "SSH 远端运行环境未通过检查，exitCode=" + exitCode;
    };
  }

  private RuntimeConfig requireConfig(ComputeEnvironmentSnapshot environment) {
    if (environment == null || environment.config() == null) {
      throw new IllegalArgumentException("运行环境配置不能为空");
    }
    return environment.config();
  }

  private SshConfig sshConfig(ComputeEnvironmentSnapshot environment) {
    SshConfig ssh = requireConfig(environment).ssh();
    if (ssh == null) {
      throw new IllegalArgumentException("SSH 运行环境缺少提交节点配置");
    }
    return ssh;
  }

  private boolean absoluteUnixPath(String value) {
    return StringUtils.hasText(value) && value.startsWith("/");
  }

  private boolean strictHostKeyChecking(SshConfig ssh) {
    return ssh.strictHostKeyChecking() == null || ssh.strictHostKeyChecking();
  }

  private Duration connectTimeout(SshConfig ssh) {
    int seconds = ssh.connectTimeoutSeconds() == null ? 5 : ssh.connectTimeoutSeconds();
    return Duration.ofSeconds(Math.max(1, Math.min(120, seconds)));
  }

  private Duration positiveDuration(Duration value, Duration fallback) {
    return value == null || value.isNegative() || value.isZero() ? fallback : value;
  }

  private int seconds(SshConfig ssh) {
    return (int) Math.max(1, Math.min(Integer.MAX_VALUE, connectTimeout(ssh).toSeconds()));
  }

  private String shellQuote(String value) {
    if (value == null) {
      throw new IllegalArgumentException("远端命令参数不能为空");
    }
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private void destroy(Process process) {
    if (process == null || !process.isAlive()) {
      return;
    }
    process.destroy();
    try {
      if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }

  private RealtimeEngineException failure(String message, boolean uncertain, Throwable cause) {
    return new RealtimeEngineException(message, uncertain, null, cause);
  }

  record ExecutionResult(int exitCode, boolean uncertain) {}

  record RemoteProbe(
      boolean cdcExecutable,
      boolean cdcVersionCommandOk,
      String cdcVersionOutput,
      boolean flinkExecutable,
      boolean flinkVersionCommandOk,
      String flinkVersionOutput,
      boolean javaExecutable,
      boolean javaVersionCommandOk,
      String javaVersionOutput,
      boolean tempWritable) {}
}
