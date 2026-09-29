package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlinkCdcEngineGatewayTest {

  private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

  @TempDir Path temp;

  private HttpServer server;
  private FlinkCdcEngineGateway gateway;
  private ComputeEnvironmentSnapshot environment;
  private final AtomicBoolean cancelled = new AtomicBoolean();

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/overview", exchange -> json(exchange, 200, "{\"jobs-running\":1}"));
    server.createContext("/jobs/", this::jobApi);
    server.start();

    Path cdcHome = Files.createDirectories(temp.resolve("flink-cdc/bin")).getParent();
    Path cli = cdcHome.resolve("bin/flink-cdc.sh");
    Files.writeString(
        cli,
        "#!/bin/sh\n"
            + "if grep -q '\\${SECRET:' \"$1\"; then exit 9; fi\n"
            + "echo 'Pipeline has been submitted to cluster.'\n"
            + "echo 'Job ID: "
            + JOB_ID
            + "'\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));

    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setWorkDirectory(temp.resolve("work").toString());
    gateway = new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    environment =
        new ComputeEnvironmentSnapshot(
            3L,
            "local-env",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                temp.resolve("flink").toString(),
                cdcHome.toString(),
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
  void submitsWithExplicitEnvironmentThenUsesJobScopedRestApis() throws Exception {
    String yaml = pipelineYaml();
    RealtimeEngineGateway.DeployResult deployed;
    try (RealtimeDeployRequest request = request(yaml, "test-key")) {
      deployed = gateway.deploy(environment, request);
    }

    assertThat(deployed.jobId()).isEqualTo(JOB_ID);
    assertThat(gateway.status(environment, JOB_ID).state())
        .isEqualTo(RealtimeEngineGateway.RuntimeStatus.State.RUNNING);
    assertThat(gateway.health(environment).path("jobs-running").asInt()).isEqualTo(1);
    assertThat(gateway.capabilities(environment).path("deployEnabled").asBoolean()).isTrue();
    assertThat(gateway.capabilities(environment).path("submissionMode").asText()).isEqualTo("LOCAL");

    Path logs = temp.resolve("work/logs");
    assertThat(logs.resolve("submit-test-key.log")).exists();
    assertThat(logs.resolve(JOB_ID + ".submit.log")).exists();
    assertThat(Files.readString(logs.resolve("submit-test-key.log"), StandardCharsets.UTF_8))
        .doesNotContain("source-secret", "sink-secret");

    gateway.stop(environment, JOB_ID);
    assertThat(cancelled).isTrue();
    try (var files = Files.list(temp.resolve("work/pipelines"))) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void redactsFailedSubmitOutputRetainsLogAndDeletesPipelineFile() throws Exception {
    Path cli = temp.resolve("flink-cdc/bin/flink-cdc.sh");
    Files.writeString(
        cli,
        "#!/bin/sh\necho 'password=source-secret connector rejected configuration'\nexit 7\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));

    try (RealtimeDeployRequest request = request(pipelineYaml(), "failed-key")) {
      assertThatThrownBy(() -> gateway.deploy(environment, request))
          .isInstanceOf(RealtimeEngineException.class)
          .hasMessageContaining("exitCode=7")
          .hasMessageContaining("password=******")
          .hasMessageNotContaining("source-secret")
          .hasMessageNotContaining("sink-secret");
    }

    Path retained = temp.resolve("work/logs/submit-failed-key.log");
    assertThat(retained).exists();
    assertThat(Files.readString(retained, StandardCharsets.UTF_8))
        .contains("password=******")
        .doesNotContain("source-secret");
    try (var files = Files.list(temp.resolve("work/pipelines"))) {
      assertThat(files).isEmpty();
    }
  }

  @Test
  void redactsRawAndYamlEscapedSecretsBeforeRetainingCliOutput() throws Exception {
    Path cli = temp.resolve("flink-cdc/bin/flink-cdc.sh");
    Files.writeString(
        cli,
        "#!/bin/sh\ncat \"$1\"\necho \"connector rejected password=source''secret\"\nexit 7\n",
        StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(cli, PosixFilePermissions.fromString("rwx------"));

    try (RealtimeDeployRequest request =
        request(pipelineYaml(), "escaped-secret", "source'secret", "sink'secret")) {
      assertThatThrownBy(() -> gateway.deploy(environment, request))
          .isInstanceOf(RealtimeEngineException.class)
          .hasMessageNotContaining("source'secret")
          .hasMessageNotContaining("source''secret")
          .hasMessageNotContaining("sink'secret")
          .hasMessageNotContaining("sink''secret");
    }

    String retained =
        Files.readString(
            temp.resolve("work/logs/submit-escaped-secret.log"), StandardCharsets.UTF_8);
    assertThat(retained)
        .contains("password: ******")
        .doesNotContain("source'secret", "source''secret", "sink'secret", "sink''secret");
  }

  private RealtimeDeployRequest request(String yaml, String key) {
    return request(yaml, key, "source-secret", "sink-secret");
  }

  private RealtimeDeployRequest request(
      String yaml, String key, String sourcePassword, String sinkPassword) {
    return new RealtimeDeployRequest(
        yaml,
        key,
        new RealtimeDeployRequest.CredentialBinding("source", sourcePassword),
        new RealtimeDeployRequest.CredentialBinding("sink", sinkPassword));
  }

  private String pipelineYaml() {
    return "source:\n  password: ${SECRET:source.password}\n"
        + "sink:\n  password: ${SECRET:sink.password}\n"
        + "pipeline:\n  name: test\n";
  }

  private void jobApi(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if ("PATCH".equals(exchange.getRequestMethod()) && path.equals("/jobs/" + JOB_ID)) {
      cancelled.set(true);
      json(exchange, 202, "{}");
    } else if (path.equals("/jobs/" + JOB_ID)) {
      json(exchange, 200, "{\"jid\":\"" + JOB_ID + "\",\"state\":\"RUNNING\"}");
    } else {
      json(exchange, 404, "{}");
    }
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
