package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Submits Flink CDC YAML for an explicit environment and manages jobs through direct Flink REST. */
@Component
public class FlinkCdcEngineGateway implements RealtimeEngineGateway {

  private static final Pattern JOB_ID = Pattern.compile("(?i)\\b[0-9a-f]{32}\\b");
  private static final Pattern SUBMITTED_JOB_ID = Pattern.compile("(?i)Job ID:\\s*([0-9a-f]{32})");
  private static final Pattern KEY_VALUE_SECRET =
      Pattern.compile("(?i)((?:password|pwd|token|secret)\\s*[=:]\\s*)[^\\s,;]+");
  private static final Pattern URL_PASSWORD = Pattern.compile("(?i)(://[^:/?#\\s]+:)[^@/?#\\s]+@");
  private static final Set<String> ACTIVE_STATES =
      Set.of(
          "CREATED",
          "SCHEDULED",
          "DEPLOYING",
          "INITIALIZING",
          "RUNNING",
          "RESTARTING",
          "RECONCILING",
          "FAILING",
          "CANCELLING");
  private static final Set<String> TERMINAL_STATES =
      Set.of("FINISHED", "CANCELED", "FAILED", "SUSPENDED");
  private static final String DELIVERY_SEMANTICS = "at-least-once";
  private static final int SUBMISSION_ERROR_TAIL_LINES = 20;
  private static final int DESTROY_WAIT_SECONDS = 5;

  private final HttpClient client;
  private final ObjectMapper json;
  private final RealtimeSyncProperties properties;
  private final SshFlinkCdcCommandRunner sshRunner = new SshFlinkCdcCommandRunner();

  public FlinkCdcEngineGateway(
      @Qualifier("realtimeHttpClient") HttpClient client,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      RealtimeSyncProperties properties) {
    this.client = client;
    this.json = json;
    this.properties = properties;
  }

  @Override
  public JsonNode health(ComputeEnvironmentSnapshot environment) {
    return getJson(environment, "/overview", false, false);
  }

  @Override
  public JsonNode capabilities(ComputeEnvironmentSnapshot environment) {
    RuntimeConfig config = requireConfig(environment);
    boolean ssh = sshSubmission(environment);
    ObjectNode result = json.createObjectNode();
    boolean deployEnabled;
    String disabledReason = null;
    if (ssh) {
      disabledReason = sshRunner.configurationError(environment);
      deployEnabled = disabledReason == null;
    } else {
      Path cli = cliPath(environment);
      deployEnabled = Files.isRegularFile(cli) && Files.isExecutable(cli);
      if (!deployEnabled) {
        disabledReason = "未找到可执行的 Flink CDC CLI：" + cli;
      }
    }

    result.put("engineType", "flink-cdc-cli");
    result.put("runtimeVersion", "flink-cdc-cli-" + config.flinkCdcVersion());
    result.put("runtimeEnvironmentId", environment.id());
    result.put("runtimeEnvironmentName", environment.name());
    result.put("runtimeEnvironmentVersion", environment.version());
    result.put("flinkVersion", config.flinkVersion());
    result.put("flinkCdcVersion", config.flinkCdcVersion());
    result.put("restUrl", config.restUrl());
    result.put("restTransport", "DIRECT");
    result.put("submissionMode", ssh ? "SSH" : "LOCAL");
    result.put("submissionEndpoint", ssh ? sshRunner.endpoint(environment) : "local");
    result.put("deliverySemantics", DELIVERY_SEMANTICS);
    result.put("checkpointsApi", true);
    result.put("metricsApi", true);
    result.put("checkpointConfiguration", false);
    result.put("restartConfiguration", false);
    result.put("protocolCompatible", true);
    result.put("deployEnabled", deployEnabled);
    if (!deployEnabled) {
      result.put("deployDisabledReason", disabledReason);
    }
    ObjectNode connectors = result.putObject("connectors");
    connectors.putArray("sources").add("mysql");
    connectors.putArray("sinks").add("yak-jdbc:mysql").add("yak-jdbc:postgres");
    connectors.putArray("schemaEvolution").add("evolve").add("ignore").add("exception");
    return result;
  }

  @Override
  public ValidationResult validate(ComputeEnvironmentSnapshot environment, String pipelineYaml) {
    validatePipelineShape(pipelineYaml);
    URI rest = restUri(environment);
    if (sshSubmission(environment)) {
      sshRunner.validateReady(environment, rest);
    } else {
      Path cli = cliPath(environment);
      if (!Files.isRegularFile(cli) || !Files.isExecutable(cli)) {
        throw failure("Flink CDC CLI 不存在或不可执行：" + cli, false, null, null);
      }
    }
    health(environment);
    return new ValidationResult(true, DELIVERY_SEMANTICS);
  }

  @Override
  public DeployResult deploy(
      ComputeEnvironmentSnapshot environment, RealtimeDeployRequest request) {
    validate(environment, request.pipelineYaml());
    Path work = Path.of(properties.getWorkDirectory()).toAbsolutePath().normalize();
    Path submissionLog = null;
    Path pipelineFile = null;
    try {
      Files.createDirectories(work.resolve("logs"));
      submissionLog = submitLogByKey(request.idempotencyKey());
      String resolved = resolveSecrets(request);
      writePrivate(submissionLog, "");

      URI rest = restUri(environment);
      boolean ssh = sshSubmission(environment);
      CommandResult result;
      if (ssh) {
        SshFlinkCdcCommandRunner.ExecutionResult sshResult =
            sshRunner.submit(
                environment,
                resolved,
                submissionLog,
                rest,
                properties.getSubmitTimeout());
        result = new CommandResult(sshResult.exitCode(), sshResult.uncertain());
      } else {
        Path pipelines = Files.createDirectories(work.resolve("pipelines"));
        String safeKey = safeKey(request.idempotencyKey());
        pipelineFile = pipelines.resolve("pipeline-" + safeKey + "-" + System.nanoTime() + ".yaml");
        writePrivate(pipelineFile, resolved);
        result = submitLocal(environment, pipelineFile, submissionLog, rest);
      }

      String output = Files.readString(submissionLog, StandardCharsets.UTF_8);
      output = sanitizeOutput(output, request);
      writePrivate(submissionLog, output);
      if (result.exitCode() != 0) {
        String excerpt = tail(output, SUBMISSION_ERROR_TAIL_LINES);
        String prefix = ssh ? "SSH Flink CDC" : "Flink CDC";
        throw failure(
            prefix
                + " 提交失败，exitCode="
                + result.exitCode()
                + (excerpt.isBlank() ? "" : "：\n" + excerpt),
            result.uncertain(),
            null,
            null);
      }
      Matcher matcher = SUBMITTED_JOB_ID.matcher(output);
      if (!matcher.find()) {
        throw failure("Flink CDC CLI 未返回 jobId，提交结果不确定", true, null, null);
      }
      String jobId = matcher.group(1).toLowerCase(Locale.ROOT);
      Files.copy(submissionLog, submitLog(jobId), StandardCopyOption.REPLACE_EXISTING);
      return new DeployResult(jobId, DELIVERY_SEMANTICS);
    } catch (RealtimeEngineException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("Flink CDC 提交被中断，结果不确定", true, null, exception);
    } catch (IOException exception) {
      throw failure("无法执行 Flink CDC 提交命令：" + exception.getMessage(), false, null, exception);
    } finally {
      sanitizeLogQuietly(submissionLog, request);
      deleteQuietly(pipelineFile);
    }
  }

  private CommandResult submitLocal(
      ComputeEnvironmentSnapshot environment, Path pipelineFile, Path submissionLog, URI rest)
      throws IOException, InterruptedException {
    RuntimeConfig config = requireConfig(environment);
    int port = rest.getPort() < 0 ? defaultPort(rest) : rest.getPort();
    ProcessBuilder builder =
        new ProcessBuilder(
                cliPath(environment).toString(),
                pipelineFile.toString(),
                "--flink-home",
                Path.of(config.flinkHome()).toAbsolutePath().normalize().toString(),
                "--target",
                "remote",
                "-Drest.address=" + rest.getHost(),
                "-Drest.port=" + port)
            .redirectErrorStream(true)
            .redirectOutput(submissionLog.toFile());
    builder.environment().put("FLINK_HOME", config.flinkHome());
    if (StringUtils.hasText(config.javaHome())) {
      builder.environment().put("JAVA_HOME", config.javaHome());
    }
    Process process = builder.start();
    Duration timeout = properties.getSubmitTimeout();
    if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      process.destroyForcibly();
      process.waitFor(DESTROY_WAIT_SECONDS, TimeUnit.SECONDS);
      throw failure("Flink CDC 提交超时，结果不确定，请在 Flink UI 中核对", true, null, null);
    }
    return new CommandResult(process.exitValue(), false);
  }

  private void validatePipelineShape(String pipelineYaml) {
    if (!StringUtils.hasText(pipelineYaml)
        || !pipelineYaml.contains("source:")
        || !pipelineYaml.contains("sink:")
        || !pipelineYaml.contains("pipeline:")) {
      throw failure("Pipeline YAML 缺少 source、sink 或 pipeline 配置", false, null, null);
    }
  }

  @Override
  public RuntimeStatus status(ComputeEnvironmentSnapshot environment, String jobId) {
    requireJobId(jobId);
    JsonNode body = getJson(environment, "/jobs/" + jobId, true, true);
    if (body == null) {
      return new RuntimeStatus(jobId, RuntimeStatus.State.NONE);
    }
    String state = body.path("state").asText("UNKNOWN").toUpperCase(Locale.ROOT);
    if (ACTIVE_STATES.contains(state)) {
      return new RuntimeStatus(jobId, RuntimeStatus.State.RUNNING);
    }
    if (TERMINAL_STATES.contains(state)) {
      return new RuntimeStatus(jobId, RuntimeStatus.State.TERMINATED);
    }
    return new RuntimeStatus(jobId, RuntimeStatus.State.UNKNOWN);
  }

  @Override
  public void stop(ComputeEnvironmentSnapshot environment, String jobId) {
    requireJobId(jobId);
    send(environment, "/jobs/" + jobId, RestMethod.PATCH, true, true);
  }

  private String resolveSecrets(RealtimeDeployRequest request) {
    String yaml = request.pipelineYaml();
    if (!yaml.contains("${SECRET:source.password}") || !yaml.contains("${SECRET:sink.password}")) {
      throw failure("Pipeline YAML 缺少密码占位符，拒绝写入临时文件", false, null, null);
    }
    return yaml.replace("${SECRET:source.password}", yamlScalar(request.source().password()))
        .replace("${SECRET:sink.password}", yamlScalar(request.sink().password()));
  }

  private String yamlScalar(char[] value) {
    String secret = new String(value);
    if (secret.contains("\n") || secret.contains("\r") || secret.indexOf('\0') >= 0) {
      throw failure("数据源密码包含不支持的换行或空字符", false, null, null);
    }
    return "'" + secret.replace("'", "''") + "'";
  }

  private String sanitizeOutput(String output, RealtimeDeployRequest request) {
    String sanitized = redactSecretVariants(output, request.source().password());
    sanitized = redactSecretVariants(sanitized, request.sink().password());
    sanitized = KEY_VALUE_SECRET.matcher(sanitized).replaceAll("$1******");
    return URL_PASSWORD.matcher(sanitized).replaceAll("$1******@");
  }

  private String redactSecretVariants(String output, char[] value) {
    String secret = new String(value);
    if (secret.isEmpty()) {
      return output;
    }
    String yamlEscaped = secret.replace("'", "''");
    String sanitized = output;
    if (!yamlEscaped.equals(secret)) {
      sanitized = sanitized.replace(yamlEscaped, "******");
    }
    return sanitized.replace(secret, "******");
  }

  private void sanitizeLogQuietly(Path log, RealtimeDeployRequest request) {
    if (log == null || !Files.isRegularFile(log)) {
      return;
    }
    try {
      String output = Files.readString(log, StandardCharsets.UTF_8);
      writePrivate(log, sanitizeOutput(output, request));
    } catch (IOException ignored) {
      // Log retention is best-effort and must never mask the actual submission result.
    }
  }

  private void writePrivate(Path path, String content) throws IOException {
    Files.writeString(path, content, StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      path.toFile().setReadable(false, false);
      path.toFile().setWritable(false, false);
      path.toFile().setReadable(true, true);
      path.toFile().setWritable(true, true);
    }
  }

  /** Returns null only when Flink explicitly reports 404 and allowNotFound is true. */
  private JsonNode getJson(
      ComputeEnvironmentSnapshot environment,
      String path,
      boolean uncertain,
      boolean allowNotFound) {
    Response response = send(environment, path, RestMethod.GET, uncertain, allowNotFound);
    return response == null ? null : response.body();
  }

  private Response send(
      ComputeEnvironmentSnapshot environment,
      String path,
      RestMethod method,
      boolean uncertain,
      boolean allowNotFound) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(baseUrl(environment) + path))
              .timeout(properties.getRequestTimeout())
              .header("Accept", "application/json");
      HttpRequest request =
          switch (method) {
            case GET -> builder.GET().build();
            case PATCH -> builder.method("PATCH", HttpRequest.BodyPublishers.noBody()).build();
          };
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (allowNotFound && response.statusCode() == 404) {
        return null;
      }
      JsonNode body = parse(response.body(), uncertain);
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw failure("Flink REST HTTP " + response.statusCode(), uncertain, response.statusCode(), null);
      }
      return new Response(body);
    } catch (HttpTimeoutException exception) {
      throw failure("Flink REST 请求超时", uncertain, null, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("Flink REST 请求被中断", uncertain, null, exception);
    } catch (IOException exception) {
      throw failure("Flink REST 连接失败", uncertain, null, exception);
    }
  }

  private JsonNode parse(String value, boolean uncertain) {
    if (value == null || value.isBlank()) {
      return json.createObjectNode();
    }
    try {
      return json.readTree(value);
    } catch (JsonProcessingException exception) {
      throw failure("Flink REST 返回了无效 JSON", uncertain, null, exception);
    }
  }

  private String tail(String value, int lines) {
    return tail(value.lines().toList(), lines);
  }

  private String tail(List<String> input, int lines) {
    Deque<String> values = new ArrayDeque<>(lines);
    input.forEach(
        line -> {
          if (values.size() == lines) {
            values.removeFirst();
          }
          values.addLast(line);
        });
    return String.join(System.lineSeparator(), values);
  }

  private Path cliPath(ComputeEnvironmentSnapshot environment) {
    return Path.of(requireConfig(environment).flinkCdcHome(), "bin", "flink-cdc.sh")
        .toAbsolutePath()
        .normalize();
  }

  private String safeKey(String idempotencyKey) {
    return idempotencyKey.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private Path submitLogByKey(String idempotencyKey) {
    return Path.of(properties.getWorkDirectory(), "logs", "submit-" + safeKey(idempotencyKey) + ".log")
        .toAbsolutePath()
        .normalize();
  }

  private Path submitLog(String jobId) {
    return Path.of(properties.getWorkDirectory(), "logs", jobId + ".submit.log")
        .toAbsolutePath()
        .normalize();
  }

  private URI restUri(ComputeEnvironmentSnapshot environment) {
    String restUrl = requireConfig(environment).restUrl();
    URI uri = URI.create(restUrl);
    if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
      throw failure("Flink REST URL 无效：" + restUrl, false, null, null);
    }
    return uri;
  }

  private String baseUrl(ComputeEnvironmentSnapshot environment) {
    return requireConfig(environment).restUrl().replaceAll("/+$", "");
  }

  private RuntimeConfig requireConfig(ComputeEnvironmentSnapshot environment) {
    if (environment == null || environment.config() == null) {
      throw new IllegalArgumentException("运行环境配置不能为空");
    }
    return environment.config();
  }

  private boolean sshSubmission(ComputeEnvironmentSnapshot environment) {
    if (ComputeEnvironment.SUBMITTER_SSH.equals(environment.submitterType())) {
      return true;
    }
    if (ComputeEnvironment.SUBMITTER_LOCAL.equals(environment.submitterType())) {
      return false;
    }
    throw new IllegalArgumentException("不支持的任务提交方式：" + environment.submitterType());
  }

  private int defaultPort(URI uri) {
    return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
  }

  private void requireJobId(String jobId) {
    if (!StringUtils.hasText(jobId) || !JOB_ID.matcher(jobId).matches()) {
      throw new IllegalArgumentException("Flink jobId 格式无效");
    }
  }

  private void deleteQuietly(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // A failed cleanup must not hide the actual submission result.
    }
  }

  private RealtimeEngineException failure(
      String message, boolean uncertain, Integer status, Throwable cause) {
    return new RealtimeEngineException(message, uncertain, status, cause);
  }

  private enum RestMethod {
    GET,
    PATCH
  }

  private record CommandResult(int exitCode, boolean uncertain) {}

  private record Response(JsonNode body) {}
}
