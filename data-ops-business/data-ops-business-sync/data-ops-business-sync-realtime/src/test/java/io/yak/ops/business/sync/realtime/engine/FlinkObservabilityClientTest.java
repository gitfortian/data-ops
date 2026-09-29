package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import io.yak.ops.business.sync.realtime.domain.RealtimeObservabilityView;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlinkObservabilityClientTest {

  private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

  @TempDir Path temp;
  private HttpServer server;
  private FlinkObservabilityClient client;
  private ComputeEnvironmentSnapshot environment;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/jobs/", this::jobApi);
    server.start();

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setWorkDirectory(temp.resolve("work").toString());
    client =
        new FlinkObservabilityClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            properties,
            new RealtimeLogRedactor());
    environment =
        new ComputeEnvironmentSnapshot(
            3L,
            "test-env",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "/opt/flink",
                "/opt/flink-cdc",
                null,
                "1.20.5",
                "3.6.0"),
            1);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void normalizesJobCheckpointAndVertexMetrics() {
    RealtimeObservabilityView view = client.snapshot(environment, JOB_ID);

    assertThat(view.flinkState()).isEqualTo("RUNNING");
    assertThat(view.durationMs()).isEqualTo(5000L);
    assertThat(view.checkpoints().completed()).isEqualTo(3);
    assertThat(view.checkpoints().latestCompleted().id()).isEqualTo(9L);
    assertThat(view.checkpoints().latestCompleted().durationMs()).isEqualTo(320L);
    assertThat(view.metrics().recordsRead()).isEqualTo(1200L);
    assertThat(view.metrics().recordsReadPerSecond()).isEqualTo(24D);
    assertThat(view.metrics().recordsWritten()).isEqualTo(1188L);
    assertThat(view.metrics().recordsWrittenPerSecond()).isEqualTo(23D);
    assertThat(view.metrics().maxBackpressuredMsPerSecond()).isEqualTo(120D);
    assertThat(view.metrics().vertexCount()).isEqualTo(2);
    assertThat(view.flinkWebUrl()).contains("/#/job/" + JOB_ID + "/overview");
  }

  @Test
  void separatesSubmissionLogFromRuntimeExceptionHistory() throws Exception {
    Path logs = Files.createDirectories(temp.resolve("work/logs"));
    Files.writeString(
        logs.resolve("submit-test-key.log"),
        "submitted\npassword=plain-secret\n",
        StandardCharsets.UTF_8);

    assertThat(client.submissionLog("test-key", 20))
        .contains("submitted")
        .contains("password=******")
        .doesNotContain("plain-secret");

    RealtimeObservabilityView.RuntimeLog runtime = client.runtimeLog(environment, JOB_ID, 20);
    assertThat(runtime.rootException()).contains("boom");
    assertThat(runtime.exceptions()).hasSize(1);
    assertThat(runtime.exceptions().get(0).taskName()).isEqualTo("Sink: yak-jdbc");
  }

  private void jobApi(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if (path.equals("/jobs/" + JOB_ID)) {
      json(
          exchange,
          200,
          "{\"jid\":\""
              + JOB_ID
              + "\",\"name\":\"yak-rt-demo\",\"state\":\"RUNNING\","
              + "\"start-time\":1000,\"duration\":5000,\"vertices\":["
              + "{\"id\":\"source-vertex\",\"name\":\"Source: mysql-cdc\"},"
              + "{\"id\":\"sink-vertex\",\"name\":\"Sink: yak-jdbc\"}]}");
      return;
    }
    if (path.equals("/jobs/" + JOB_ID + "/checkpoints")) {
      json(
          exchange,
          200,
          "{\"counts\":{\"total\":4,\"completed\":3,\"failed\":1,\"in_progress\":0,\"restored\":0},"
              + "\"latest\":{\"completed\":{\"id\":9,\"trigger_timestamp\":2000,"
              + "\"latest_ack_timestamp\":2320,\"end_to_end_duration\":320,\"state_size\":4096,"
              + "\"checkpointed_size\":2048,\"num_acknowledged_subtasks\":2,\"num_subtasks\":2}}}");
      return;
    }
    if (path.equals("/jobs/" + JOB_ID + "/exceptions")) {
      json(
          exchange,
          200,
          "{\"exceptionHistory\":{\"truncated\":false,\"entries\":[{"
              + "\"exceptionName\":\"java.lang.RuntimeException\",\"stacktrace\":\"boom\\nstack\","
              + "\"timestamp\":3000,\"taskName\":\"Sink: yak-jdbc\","
              + "\"taskManagerId\":\"tm-1\",\"endpoint\":\"127.0.0.1:1234\"}]}}}");
      return;
    }
    if (path.endsWith("/subtasks/metrics")) {
      boolean source = path.contains("source-vertex");
      json(
          exchange,
          200,
          source
              ? metrics("numRecordsOut", 1200, "numRecordsOutPerSecond", 24, 50, 20, 900)
              : metrics("numRecordsIn", 1188, "numRecordsInPerSecond", 23, 700, 120, 180));
      return;
    }
    json(exchange, 404, "{}");
  }

  private String metrics(
      String recordsId,
      int records,
      String rateId,
      int rate,
      int busy,
      int backpressure,
      int idle) {
    return "["
        + metric(recordsId, records, records)
        + ","
        + metric(rateId, rate, rate)
        + ","
        + metric("busyTimeMsPerSecond", busy, busy)
        + ","
        + metric("backPressuredTimeMsPerSecond", backpressure, backpressure)
        + ","
        + metric("idleTimeMsPerSecond", idle, idle)
        + "]";
  }

  private String metric(String id, double sum, double max) {
    return "{\"id\":\""
        + id
        + "\",\"sum\":\""
        + sum
        + "\",\"avg\":\""
        + sum
        + "\",\"max\":\""
        + max
        + "\"}";
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
