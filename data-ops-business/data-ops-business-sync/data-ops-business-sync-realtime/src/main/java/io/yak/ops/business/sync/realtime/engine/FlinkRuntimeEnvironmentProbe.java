package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentDiagnosis.Check;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Performs explicit, operator-requested checks without submitting a Flink job. */
@Component
public class FlinkRuntimeEnvironmentProbe {

  private static final Pattern VERSION =
      Pattern.compile("(?i)(?:version[^0-9]*)?([0-9]+\\.[0-9]+(?:\\.[0-9]+)?(?:[-+][A-Za-z0-9._-]+)?)");
  private static final int VERSION_COMMAND_TIMEOUT_SECONDS = 8;
  private static final int MAX_DIAGNOSTIC_MESSAGE_LENGTH = 300;

  private final RealtimeEngineGateway gateway;
  private final RealtimeSyncProperties properties;
  private final SshFlinkCdcCommandRunner sshRunner;

  public FlinkRuntimeEnvironmentProbe(
      RealtimeEngineGateway gateway, RealtimeSyncProperties properties) {
    this.gateway = gateway;
    this.properties = properties;
    this.sshRunner = new SshFlinkCdcCommandRunner();
  }

  public ComputeEnvironmentDiagnosis diagnose(ComputeEnvironmentSnapshot environment) {
    if (environment == null || environment.config() == null) {
      throw new IllegalArgumentException("运行环境配置不能为空");
    }
    List<Check> checks = new ArrayList<>();
    LocalDateTime checkedAt = LocalDateTime.now();

    String detectedFlinkVersion = null;
    String detectedCdcVersion = null;
    String detectedJavaVersion = null;

    checks.add(checkWorkDirectory());

    try {
      JsonNode overview = gateway.health(environment);
      detectedFlinkVersion = text(overview, "flink-version");
      checks.add(
          pass(
              "FLINK_REST",
              "Flink REST",
              overviewMessage(overview, detectedFlinkVersion)));
    } catch (RuntimeException exception) {
      checks.add(fail("FLINK_REST", "Flink REST", safeMessage(exception, "无法连接 Flink REST")));
    }

    ProbeVersions versions;
    if (ComputeEnvironment.SUBMITTER_SSH.equals(environment.submitterType())) {
      versions = diagnoseSsh(environment, checks);
    } else {
      versions = diagnoseLocal(environment, checks);
    }
    detectedFlinkVersion = firstNonBlank(versions.flinkVersion(), detectedFlinkVersion);
    detectedCdcVersion = versions.cdcVersion();
    detectedJavaVersion = versions.javaVersion();

    String status = overallStatus(checks);
    boolean ready = !ComputeEnvironmentDiagnosis.STATUS_FAILED.equals(status);
    String summary = summary(status, checks);
    return new ComputeEnvironmentDiagnosis(
        environment.id() <= 0 ? null : environment.id(),
        environment.name(),
        status,
        ready,
        summary,
        detectedFlinkVersion,
        detectedCdcVersion,
        detectedJavaVersion,
        checkedAt,
        List.copyOf(checks));
  }

  private ProbeVersions diagnoseLocal(
      ComputeEnvironmentSnapshot environment, List<Check> checks) {
    RuntimeConfig config = environment.config();
    Path cdc = Path.of(config.flinkCdcHome(), "bin", "flink-cdc.sh").toAbsolutePath().normalize();
    Path flink = Path.of(config.flinkHome(), "bin", "flink").toAbsolutePath().normalize();

    String cdcVersion = null;
    if (!Files.isRegularFile(cdc) || !Files.isExecutable(cdc)) {
      checks.add(fail("FLINK_CDC_CLI", "Flink CDC CLI", "文件不存在或不可执行：" + cdc));
    } else {
      CommandOutput output = runVersionCommand(List.of(cdc.toString(), "--version"));
      cdcVersion = extractVersion(output.output());
      checks.add(
          versionCheck(
              "FLINK_CDC_CLI", "Flink CDC CLI", output, cdcVersion, config.flinkCdcVersion()));
    }

    String flinkVersion = null;
    if (!Files.isRegularFile(flink) || !Files.isExecutable(flink)) {
      checks.add(fail("FLINK_CLI", "Flink CLI", "文件不存在或不可执行：" + flink));
    } else {
      CommandOutput output = runVersionCommand(List.of(flink.toString(), "--version"));
      flinkVersion = extractVersion(output.output());
      checks.add(
          versionCheck("FLINK_CLI", "Flink CLI", output, flinkVersion, config.flinkVersion()));
    }

    String javaExecutable =
        StringUtils.hasText(config.javaHome())
            ? Path.of(config.javaHome(), "bin", "java").toAbsolutePath().normalize().toString()
            : "java";
    if (StringUtils.hasText(config.javaHome()) && !Files.isExecutable(Path.of(javaExecutable))) {
      checks.add(fail("JAVA", "Java", "JAVA_HOME/bin/java 不存在或不可执行"));
      return new ProbeVersions(flinkVersion, cdcVersion, null);
    }
    CommandOutput javaOutput = runVersionCommand(List.of(javaExecutable, "-version"));
    String javaVersion = extractVersion(javaOutput.output());
    if (!javaOutput.success()) {
      checks.add(fail("JAVA", "Java", nonBlank(javaOutput.output(), "无法执行 java -version")));
    } else if (!StringUtils.hasText(javaVersion)) {
      checks.add(warn("JAVA", "Java", "Java 可执行，但未能识别版本"));
    } else {
      checks.add(pass("JAVA", "Java", "检测到 Java " + javaVersion));
    }
    return new ProbeVersions(flinkVersion, cdcVersion, javaVersion);
  }

  private ProbeVersions diagnoseSsh(
      ComputeEnvironmentSnapshot environment, List<Check> checks) {
    RuntimeConfig config = environment.config();
    SshConfig ssh = config.ssh();
    if (ssh != null && StringUtils.hasText(ssh.identityFile())) {
      try {
        if (!Files.isRegularFile(Path.of(ssh.identityFile()))) {
          checks.add(fail("SSH_IDENTITY", "SSH 私钥", "配置的私钥文件不存在"));
        } else {
          checks.add(pass("SSH_IDENTITY", "SSH 私钥", "私钥文件可读取"));
        }
      } catch (RuntimeException exception) {
        checks.add(fail("SSH_IDENTITY", "SSH 私钥", "私钥文件路径无效"));
      }
    }

    try {
      URI rest = URI.create(config.restUrl());
      SshFlinkCdcCommandRunner.RemoteProbe probe = sshRunner.probe(environment, rest);
      checks.add(pass("SSH", "SSH 连接", "已连接 " + sshRunner.endpoint(environment)));

      String cdcVersion = extractVersion(probe.cdcVersionOutput());
      checks.add(
          remoteVersionCheck(
              "FLINK_CDC_CLI",
              "Flink CDC CLI",
              probe.cdcExecutable(),
              probe.cdcVersionCommandOk(),
              cdcVersion,
              config.flinkCdcVersion(),
              "远端 Flink CDC CLI 不存在或不可执行"));

      String flinkVersion = extractVersion(probe.flinkVersionOutput());
      checks.add(
          remoteVersionCheck(
              "FLINK_CLI",
              "Flink CLI",
              probe.flinkExecutable(),
              probe.flinkVersionCommandOk(),
              flinkVersion,
              config.flinkVersion(),
              "远端 Flink CLI 不存在或不可执行"));

      String javaVersion = extractVersion(probe.javaVersionOutput());
      if (!probe.javaExecutable()) {
        checks.add(fail("JAVA", "Java", "远端 Java 不存在或不可执行"));
      } else if (!probe.javaVersionCommandOk()) {
        checks.add(warn("JAVA", "Java", "远端 Java 可执行，但 java -version 未正常返回"));
      } else if (!StringUtils.hasText(javaVersion)) {
        checks.add(warn("JAVA", "Java", "远端 Java 可执行，但未能识别版本"));
      } else {
        checks.add(pass("JAVA", "Java", "检测到 Java " + javaVersion));
      }

      checks.add(
          probe.tempWritable()
              ? pass("REMOTE_TEMP", "远端临时目录", "mktemp 可创建并清理临时文件")
              : fail("REMOTE_TEMP", "远端临时目录", "无法创建远端临时 pipeline 文件"));
      return new ProbeVersions(flinkVersion, cdcVersion, javaVersion);
    } catch (RuntimeException exception) {
      checks.add(fail("SSH", "SSH 连接", safeMessage(exception, "SSH 连接或认证失败")));
      checks.add(warn("FLINK_CDC_CLI", "Flink CDC CLI", "SSH 未连接，未执行远端检查"));
      checks.add(warn("FLINK_CLI", "Flink CLI", "SSH 未连接，未执行远端检查"));
      checks.add(warn("JAVA", "Java", "SSH 未连接，未执行远端检查"));
      checks.add(warn("REMOTE_TEMP", "远端临时目录", "SSH 未连接，未执行远端检查"));
      return new ProbeVersions(null, null, null);
    }
  }

  private Check checkWorkDirectory() {
    Path marker = null;
    try {
      Path work = Path.of(properties.getWorkDirectory()).toAbsolutePath().normalize();
      Files.createDirectories(work);
      marker = Files.createTempFile(work, ".yak-env-probe-", ".tmp");
      return pass("WORK_DIRECTORY", "Yak Ops 工作目录", "可读写：" + work);
    } catch (IOException | RuntimeException exception) {
      return fail("WORK_DIRECTORY", "Yak Ops 工作目录", safeMessage(exception, "工作目录不可写"));
    } finally {
      if (marker != null) {
        try {
          Files.deleteIfExists(marker);
        } catch (IOException ignored) {
          // Best-effort cleanup for an explicit diagnostics probe.
        }
      }
    }
  }

  private Check versionCheck(
      String key, String label, CommandOutput output, String detected, String configured) {
    if (!output.success()) {
      return warn(key, label, nonBlank(output.output(), label + " 可执行，但版本命令返回异常"));
    }
    return versionResult(key, label, detected, configured);
  }

  private Check remoteVersionCheck(
      String key,
      String label,
      boolean executable,
      boolean versionCommandOk,
      String detected,
      String configured,
      String missingMessage) {
    if (!executable) {
      return fail(key, label, missingMessage);
    }
    if (!versionCommandOk) {
      return warn(key, label, label + " 可执行，但 --version 未正常返回");
    }
    return versionResult(key, label, detected, configured);
  }

  private Check versionResult(
      String key, String label, String detected, String configured) {
    if (!StringUtils.hasText(detected)) {
      return warn(key, label, label + " 可执行，但未能识别版本");
    }
    if (StringUtils.hasText(configured) && !sameVersion(configured, detected)) {
      return warn(key, label, "检测到 " + detected + "，当前配置为 " + configured);
    }
    return pass(key, label, "检测到 " + detected);
  }

  private CommandOutput runVersionCommand(List<String> command) {
    Process process = null;
    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      if (!process.waitFor(VERSION_COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new CommandOutput(false, "版本检测超时");
      }
      String output =
          new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      return new CommandOutput(process.exitValue() == 0, output);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      if (process != null) {
        process.destroyForcibly();
      }
      return new CommandOutput(false, "版本检测被中断");
    } catch (IOException exception) {
      if (process != null) {
        process.destroyForcibly();
      }
      return new CommandOutput(false, "无法执行命令：" + exception.getMessage());
    }
  }

  private String extractVersion(String output) {
    if (!StringUtils.hasText(output)) {
      return null;
    }
    for (String line : output.lines().toList()) {
      if (!line.toLowerCase(Locale.ROOT).contains("version")) {
        continue;
      }
      Matcher matcher = VERSION.matcher(line);
      if (matcher.find()) {
        return matcher.group(1);
      }
    }
    Matcher matcher = VERSION.matcher(output);
    return matcher.find() ? matcher.group(1) : null;
  }

  private boolean sameVersion(String configured, String detected) {
    return configured.trim().equalsIgnoreCase(detected.trim());
  }

  private String overviewMessage(JsonNode overview, String version) {
    List<String> parts = new ArrayList<>();
    if (StringUtils.hasText(version)) {
      parts.add("Flink " + version);
    }
    addNumber(parts, overview, "taskmanagers", "TaskManagers");
    if (overview != null && overview.has("slots-available") && overview.has("slots-total")) {
      parts.add(
          "Slots "
              + overview.path("slots-available").asInt()
              + "/"
              + overview.path("slots-total").asInt());
    }
    addNumber(parts, overview, "jobs-running", "Running Jobs");
    return parts.isEmpty() ? "Flink REST 可访问" : String.join(" · ", parts);
  }

  private void addNumber(List<String> parts, JsonNode node, String field, String label) {
    if (node != null && node.has(field) && node.path(field).isNumber()) {
      parts.add(label + " " + node.path(field).asInt());
    }
  }

  private String overallStatus(List<Check> checks) {
    if (checks.stream().anyMatch(item -> Check.FAIL.equals(item.status()))) {
      return ComputeEnvironmentDiagnosis.STATUS_FAILED;
    }
    if (checks.stream().anyMatch(item -> Check.WARN.equals(item.status()))) {
      return ComputeEnvironmentDiagnosis.STATUS_WARNING;
    }
    return ComputeEnvironmentDiagnosis.STATUS_HEALTHY;
  }

  private String summary(String status, List<Check> checks) {
    if (ComputeEnvironmentDiagnosis.STATUS_FAILED.equals(status)) {
      return checks.stream()
          .filter(item -> Check.FAIL.equals(item.status()))
          .findFirst()
          .map(item -> item.label() + "：" + item.message())
          .orElse("运行环境检测失败");
    }
    long warnings = checks.stream().filter(item -> Check.WARN.equals(item.status())).count();
    if (warnings > 0) {
      return "环境可用，但有 " + warnings + " 项需要关注";
    }
    return "环境检查通过，可以提交实时同步任务";
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
      return null;
    }
    String value = node.path(field).asText(null);
    return StringUtils.hasText(value) ? value.trim() : null;
  }

  private String firstNonBlank(String first, String second) {
    return StringUtils.hasText(first) ? first : second;
  }

  private String nonBlank(String value, String fallback) {
    if (!StringUtils.hasText(value)) {
      return fallback;
    }
    String firstLine = value.lines().findFirst().orElse(value).trim();
    return firstLine.length() > MAX_DIAGNOSTIC_MESSAGE_LENGTH
        ? firstLine.substring(0, MAX_DIAGNOSTIC_MESSAGE_LENGTH)
        : firstLine;
  }

  private String safeMessage(Throwable exception, String fallback) {
    return nonBlank(exception == null ? null : exception.getMessage(), fallback);
  }

  private Check pass(String key, String label, String message) {
    return new Check(key, label, Check.PASS, message);
  }

  private Check warn(String key, String label, String message) {
    return new Check(key, label, Check.WARN, message);
  }

  private Check fail(String key, String label, String message) {
    return new Check(key, label, Check.FAIL, message);
  }

  private record CommandOutput(boolean success, String output) {}

  private record ProbeVersions(String flinkVersion, String cdcVersion, String javaVersion) {}
}
