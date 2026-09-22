package io.yak.ops.business.sync.realtime.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.CheckpointDetail;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.CheckpointSummary;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.MetricSummary;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeExceptionEntry;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView.RuntimeLog;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Read-only Flink REST/file adapter that normalizes runtime observability for the Yak Ops UI. */
@Component
public class FlinkObservabilityClient {

  private static final Pattern JOB_ID = Pattern.compile("(?i)[0-9a-f]{32}");
  private static final int MAX_RUNTIME_EXCEPTIONS = 100;
  private static final String METRIC_IDS =
      String.join(
          ",",
          "numRecordsIn",
          "numRecordsInPerSecond",
          "numRecordsOut",
          "numRecordsOutPerSecond",
          "numBytesIn",
          "numBytesInPerSecond",
          "numBytesOut",
          "numBytesOutPerSecond",
          "busyTimeMsPerSecond",
          "backPressuredTimeMsPerSecond",
          "idleTimeMsPerSecond");

  private final HttpClient client;
  private final ObjectMapper json;
  private final RealtimeSyncProperties properties;
  private final RealtimeLogRedactor redactor;

  public FlinkObservabilityClient(
      @Qualifier("realtimeHttpClient") HttpClient client,
      @Qualifier("realtimeObjectMapper") ObjectMapper json,
      RealtimeSyncProperties properties,
      RealtimeLogRedactor redactor) {
    this.client = client;
    this.json = json;
    this.properties = properties;
    this.redactor = redactor;
  }

  public RealtimeObservabilityView snapshot(
      ComputeEnvironmentSnapshot environment, String jobId) {
    requireJobId(jobId);
    JsonNode job = getJson(environment, "/jobs/" + jobId, true);
    long sampledAt = System.currentTimeMillis();
    if (job == null) {
      return new RealtimeObservabilityView(
          jobId,
          null,
          "NOT_FOUND",
          null,
          null,
          flinkWebUrl(environment, jobId),
          sampledAt,
          emptyCheckpointSummary(),
          emptyMetricSummary());
    }

    JsonNode checkpointBody = getJson(environment, "/jobs/" + jobId + "/checkpoints", true);
    return new RealtimeObservabilityView(
        jobId,
        text(job, "name"),
        text(job, "state"),
        number(job, "start-time"),
        number(job, "duration"),
        flinkWebUrl(environment, jobId),
        sampledAt,
        checkpointSummary(checkpointBody),
        metricSummary(environment, jobId, job));
  }

  public String submissionLog(String idempotencyKey, int tailLines) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key 不能为空");
    }
    int tail = Math.max(1, Math.min(tailLines, properties.getMaxLogLines()));
    Path log =
        Path.of(
                properties.getWorkDirectory(),
                "logs",
                "submit-" + safeKey(idempotencyKey) + ".log")
            .toAbsolutePath()
            .normalize();
    if (!Files.isRegularFile(log)) {
      return "";
    }
    try (var lines = Files.lines(log, StandardCharsets.UTF_8)) {
      return redactor.redact(tail(lines.toList(), tail));
    } catch (IOException exception) {
      throw failure("无法读取 Flink CDC 提交日志", false, exception);
    }
  }

  public RuntimeLog runtimeLog(
      ComputeEnvironmentSnapshot environment, String jobId, int maxExceptions) {
    requireJobId(jobId);
    int limit = Math.max(1, Math.min(maxExceptions, MAX_RUNTIME_EXCEPTIONS));
    JsonNode body =
        getJson(environment, "/jobs/" + jobId + "/exceptions?maxExceptions=" + limit, true);
    if (body == null) {
      return new RuntimeLog(null, null, false, List.of());
    }

    JsonNode history = body.path("exceptionHistory");
    List<RuntimeExceptionEntry> entries = new ArrayList<>();
    if (history.path("entries").isArray()) {
      for (JsonNode entry : history.path("entries")) {
        addException(entries, entry);
        JsonNode concurrent = entry.path("concurrentExceptions");
        if (concurrent.isArray()) {
          for (JsonNode nested : concurrent) {
            addException(entries, nested);
          }
        }
      }
    }

    String root = entries.isEmpty() ? text(body, "root-exception") : entries.get(0).stacktrace();
    Long timestamp = entries.isEmpty() ? number(body, "timestamp") : entries.get(0).timestamp();
    boolean truncated = history.path("truncated").asBoolean(body.path("truncated").asBoolean(false));
    return new RuntimeLog(redactor.redact(root), timestamp, truncated, entries);
  }

  private void addException(List<RuntimeExceptionEntry> entries, JsonNode node) {
    String stacktrace = text(node, "stacktrace");
    if (stacktrace == null) {
      stacktrace = text(node, "exception");
    }
    entries.add(
        new RuntimeExceptionEntry(
            text(node, "exceptionName"),
            redactor.redact(stacktrace),
            number(node, "timestamp"),
            firstText(node, "taskName", "task"),
            text(node, "taskManagerId"),
            firstText(node, "endpoint", "location")));
  }

  private CheckpointSummary checkpointSummary(JsonNode body) {
    if (body == null || body.isMissingNode() || body.isNull()) {
      return emptyCheckpointSummary();
    }
    JsonNode counts = body.path("counts");
    JsonNode latest = body.path("latest");
    return new CheckpointSummary(
        counts.path("total").asLong(0),
        counts.path("completed").asLong(0),
        counts.path("failed").asLong(0),
        counts.path("in_progress").asLong(0),
        counts.path("restored").asLong(0),
        checkpointDetail(latest.path("completed")),
        checkpointDetail(latest.path("failed")));
  }

  private CheckpointDetail checkpointDetail(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull() || node.isEmpty()) {
      return null;
    }
    return new CheckpointDetail(
        number(node, "id"),
        number(node, "trigger_timestamp"),
        number(node, "latest_ack_timestamp"),
        number(node, "end_to_end_duration"),
        number(node, "state_size"),
        number(node, "checkpointed_size"),
        integer(node, "num_acknowledged_subtasks"),
        integer(node, "num_subtasks"),
        redactor.redact(firstText(node, "failure_message", "failure-message")));
  }

  private MetricSummary metricSummary(
      ComputeEnvironmentSnapshot environment, String jobId, JsonNode job) {
    JsonNode vertices = job.path("vertices");
    if (!vertices.isArray()) {
      return emptyMetricSummary();
    }

    Double recordsRead = null;
    Double recordsReadRate = null;
    Double recordsWritten = null;
    Double recordsWrittenRate = null;
    Double bytesRead = null;
    Double bytesReadRate = null;
    Double bytesWritten = null;
    Double bytesWrittenRate = null;
    Double maxBusy = null;
    Double maxBackpressure = null;
    Double maxIdle = null;
    int vertexCount = 0;

    for (JsonNode vertex : vertices) {
      String vertexId = text(vertex, "id");
      if (vertexId == null || vertexId.isBlank()) {
        continue;
      }
      vertexCount++;
      Map<String, MetricAggregate> metrics;
      try {
        metrics = vertexMetrics(environment, jobId, vertexId);
      } catch (RealtimeEngineException ignored) {
        continue;
      }
      String vertexName = text(vertex, "name");
      String normalizedName = vertexName == null ? "" : vertexName.toLowerCase(Locale.ROOT);
      boolean source = normalizedName.contains("source");
      boolean sink = normalizedName.contains("sink");

      if (source) {
        recordsRead = add(recordsRead, sum(metrics, "numRecordsOut"));
        recordsReadRate = add(recordsReadRate, sum(metrics, "numRecordsOutPerSecond"));
        bytesRead = add(bytesRead, sum(metrics, "numBytesOut"));
        bytesReadRate = add(bytesReadRate, sum(metrics, "numBytesOutPerSecond"));
      }
      if (sink) {
        recordsWritten = add(recordsWritten, sum(metrics, "numRecordsIn"));
        recordsWrittenRate = add(recordsWrittenRate, sum(metrics, "numRecordsInPerSecond"));
        bytesWritten = add(bytesWritten, sum(metrics, "numBytesIn"));
        bytesWrittenRate = add(bytesWrittenRate, sum(metrics, "numBytesInPerSecond"));
      }
      maxBusy = max(maxBusy, max(metrics, "busyTimeMsPerSecond"));
      maxBackpressure = max(maxBackpressure, max(metrics, "backPressuredTimeMsPerSecond"));
      maxIdle = max(maxIdle, max(metrics, "idleTimeMsPerSecond"));
    }

    return new MetricSummary(
        rounded(recordsRead),
        recordsReadRate,
        rounded(recordsWritten),
        recordsWrittenRate,
        rounded(bytesRead),
        bytesReadRate,
        rounded(bytesWritten),
        bytesWrittenRate,
        maxBusy,
        maxBackpressure,
        maxIdle,
        vertexCount);
  }

  private Map<String, MetricAggregate> vertexMetrics(
      ComputeEnvironmentSnapshot environment, String jobId, String vertexId) {
    JsonNode body =
        getJson(
            environment,
            "/jobs/"
                + jobId
                + "/vertices/"
                + vertexId
                + "/subtasks/metrics?get="
                + METRIC_IDS
                + "&agg=sum,avg,max",
            true);
    Map<String, MetricAggregate> result = new HashMap<>();
    if (body == null || !body.isArray()) {
      return result;
    }
    for (JsonNode metric : body) {
      String id = text(metric, "id");
      if (id != null) {
        result.put(
            id,
            new MetricAggregate(
                decimal(metric.get("sum")),
                decimal(metric.get("avg")),
                decimal(metric.get("max"))));
      }
    }
    return result;
  }

  private Double sum(Map<String, MetricAggregate> metrics, String id) {
    MetricAggregate metric = metrics.get(id);
    return metric == null ? null : metric.sum();
  }

  private Double max(Map<String, MetricAggregate> metrics, String id) {
    MetricAggregate metric = metrics.get(id);
    return metric == null ? null : metric.max();
  }

  private Double add(Double left, Double right) {
    if (right == null) {
      return left;
    }
    return (left == null ? 0D : left) + right;
  }

  private Double max(Double left, Double right) {
    if (right == null) {
      return left;
    }
    return left == null ? right : Math.max(left, right);
  }

  private Long rounded(Double value) {
    return value == null ? null : Math.round(value);
  }

  private Double decimal(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    try {
      return Double.valueOf(node.asText());
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private String firstText(JsonNode node, String first, String second) {
    String value = text(node, first);
    return value == null ? text(node, second) : value;
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
      return null;
    }
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value;
  }

  private Long number(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    if (value == null || value.isNull()) {
      return null;
    }
    return value.isNumber() ? value.longValue() : parseLong(value.asText());
  }

  private Integer integer(JsonNode node, String field) {
    Long value = number(node, field);
    return value == null ? null : value.intValue();
  }

  private Long parseLong(String value) {
    try {
      return value == null || value.isBlank() ? null : Long.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  /** Returns null only when Flink explicitly reports 404 and allowNotFound is true. */
  private JsonNode getJson(
      ComputeEnvironmentSnapshot environment, String path, boolean allowNotFound) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl(environment) + path))
              .timeout(properties.getRequestTimeout())
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (allowNotFound && response.statusCode() == 404) {
        return null;
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw failure("Flink REST HTTP " + response.statusCode(), true, null);
      }
      return parseJson(response.body());
    } catch (HttpTimeoutException exception) {
      throw failure("Flink REST 请求超时", true, exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("Flink REST 请求被中断", true, exception);
    } catch (IOException exception) {
      throw failure("Flink REST 连接失败", true, exception);
    }
  }

  private JsonNode parseJson(String body) {
    if (body == null || body.isBlank()) {
      return json.createObjectNode();
    }
    try {
      return json.readTree(body);
    } catch (JsonProcessingException exception) {
      throw failure("Flink REST 返回了无效 JSON", true, exception);
    }
  }

  private CheckpointSummary emptyCheckpointSummary() {
    return new CheckpointSummary(0, 0, 0, 0, 0, null, null);
  }

  private MetricSummary emptyMetricSummary() {
    return new MetricSummary(null, null, null, null, null, null, null, null, null, null, null, 0);
  }

  private String flinkWebUrl(ComputeEnvironmentSnapshot environment, String jobId) {
    return baseUrl(environment) + "/#/job/" + jobId + "/overview";
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

  private String safeKey(String idempotencyKey) {
    return idempotencyKey.replaceAll("[^A-Za-z0-9._-]", "_");
  }

  private void requireJobId(String jobId) {
    if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
      throw new IllegalArgumentException("Flink jobId 格式无效");
    }
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

  private RealtimeEngineException failure(String message, boolean uncertain, Throwable cause) {
    return new RealtimeEngineException(message, uncertain, null, cause);
  }

  private record MetricAggregate(Double sum, Double avg, Double max) {}
}
