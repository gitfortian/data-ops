package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.SshConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlinkCdcEngineGatewaySshTest {

  private static final String JOB_ID = "0123456789abcdef0123456789abcdef";

  @TempDir Path temp;
  private HttpServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/overview", exchange -> json(exchange, 200, "{\"jobs-running\":0}"));
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void submitsThroughExplicitSshEnvironmentWithoutCreatingLocalPipelineFile() throws Exception {
    Path captured = temp.resolve("remote-pipeline.yaml");
    Path ssh = fakeSsh(captured, 0);
    RealtimeSyncProperties properties = appProperties("work");
    FlinkCdcEngineGateway gateway =
        new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    ComputeEnvironmentSnapshot environment = sshEnvironment(ssh, "10.0.0.20", 22);

    RealtimeEngineGateway.DeployResult result;
    try (RealtimeDeployRequest request = request(pipelineYaml(), "ssh-key")) {
      result = gateway.deploy(environment, request);
    }

    assertThat(result.jobId()).isEqualTo(JOB_ID);
    assertThat(gateway.capabilities(environment).path("submissionMode").asText()).isEqualTo("SSH");
    assertThat(gateway.capabilities(environment).path("submissionEndpoint").asText())
        .isEqualTo("flink@10.0.0.20:22");
    assertThat(Files.readString(captured, StandardCharsets.UTF_8))
        .contains("password: 'source-secret'")
        .contains("password: 'sink-secret'")
        .doesNotContain("${SECRET:");
    assertThat(temp.resolve("work/pipelines")).doesNotExist();
    assertThat(temp.resolve("work/logs/submit-ssh-key.log")).exists();
    assertThat(temp.resolve("work/logs/" + JOB_ID + ".submit.log")).exists();
  }

  @Test
  void probesSshRuntimeWithoutSubmittingPipeline() throws Exception {
    Path captured = temp.resolve("should-not-contain-pipeline.yaml");
    Path ssh = fakeSsh(captured, 0);
    SshFlinkCdcCommandRunner runner = new SshFlinkCdcCommandRunner();
    ComputeEnvironmentSnapshot environment = sshEnvironment(ssh, "10.0.0.99", 2202);

    SshFlinkCdcCommandRunner.RemoteProbe result =
        runner.probe(environment, URI.create(environment.config().restUrl()));

    assertThat(result.cdcExecutable()).isTrue();
    assertThat(result.cdcVersionCommandOk()).isTrue();
    assertThat(result.cdcVersionOutput()).contains("3.6.0");
    assertThat(result.flinkExecutable()).isTrue();
    assertThat(result.flinkVersionOutput()).contains("1.20.5");
    assertThat(result.javaExecutable()).isTrue();
    assertThat(result.javaVersionOutput()).contains("17.0.12");
    assertThat(result.tempWritable()).isTrue();
    assertThat(captured).doesNotExist();
  }

  @Test
  void propagatesSsh255AsUncertainEngineFailureAndRetainsLog() throws Exception {
    Path ssh = fakeSsh(temp.resolve("remote-uncertain.yaml"), 255);
    RealtimeSyncProperties properties = appProperties("work-uncertain");
    FlinkCdcEngineGateway gateway =
        new FlinkCdcEngineGateway(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    ComputeEnvironmentSnapshot environment = sshEnvironment(ssh, "10.0.0.20", 22);

    try (RealtimeDeployRequest request = request(pipelineYaml(), "ssh-uncertain")) {
      assertThatThrownBy(() -> gateway.deploy(environment, request))
          .isInstanceOf(RealtimeEngineException.class)
          .satisfies(error -> assertThat(((RealtimeEngineException) error).uncertain()).isTrue())
          .hasMessageContaining("exitCode=255");
    }

    assertThat(temp.resolve("work-uncertain/logs/submit-ssh-uncertain.log")).exists();
  }

  private RealtimeSyncProperties appProperties(String workDirectory) {
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    properties.setWorkDirectory(temp.resolve(workDirectory).toString());
    properties.setSubmitTimeout(Duration.ofSeconds(5));
    return properties;
  }

  private ComputeEnvironmentSnapshot sshEnvironment(Path executable, String host, int port) {
    int restPort = server.getAddress().getPort();
    RuntimeConfig config =
        new RuntimeConfig(
            "http://127.0.0.1:" + restPort,
            "/opt/flink",
            "/opt/flink-cdc",
            null,
            "1.20.5",
            "3.6.0",
            new SshConfig(
                executable.toString(),
                host,
                port,
                "flink",
                null,
                null,
                true,
                1,
                "127.0.0.1",
                restPort));
    return new ComputeEnvironmentSnapshot(
        9L,
        "ssh-env",
        ComputeEnvironment.ENGINE_FLINK_CDC,
        ComputeEnvironment.DEPLOYMENT_REMOTE,
        ComputeEnvironment.SUBMITTER_SSH,
        config,
        3);
  }

  private RealtimeDeployRequest request(String yaml, String key) {
    return new RealtimeDeployRequest(
        yaml,
        key,
        new RealtimeDeployRequest.CredentialBinding("source", "source-secret"),
        new RealtimeDeployRequest.CredentialBinding("sink", "sink-secret"));
  }

  private String pipelineYaml() {
    return "source:\n  password: ${SECRET:source.password}\n"
        + "sink:\n  password: ${SECRET:sink.password}\n"
        + "pipeline:\n  name: test\n";
  }

  private Path fakeSsh(Path captured, int submitExitCode) throws Exception {
    Path script =
        temp.resolve("gateway-fake-ssh-" + submitExitCode + "-" + captured.getFileName() + ".sh");
    String content =
        "#!/bin/sh\n"
            + "case \"$*\" in\n"
            + "  *YAK_REALTIME_SSH_READY*) exit 0 ;;\n"
            + "  *YAK_CDC_EXEC*)\n"
            + "    echo 'YAK_SSH=1'\n"
            + "    echo 'YAK_CDC_EXEC=1'\n"
            + "    echo 'YAK_CDC_VERSION_OK=1'\n"
            + "    echo 'YAK_CDC_VERSION=Flink CDC version 3.6.0'\n"
            + "    echo 'YAK_FLINK_EXEC=1'\n"
            + "    echo 'YAK_FLINK_VERSION_OK=1'\n"
            + "    echo 'YAK_FLINK_VERSION=Version: 1.20.5'\n"
            + "    echo 'YAK_JAVA_EXEC=1'\n"
            + "    echo 'YAK_JAVA_VERSION_OK=1'\n"
            + "    echo 'YAK_JAVA_VERSION=openjdk version 17.0.12'\n"
            + "    echo 'YAK_TEMP=1'\n"
            + "    exit 0 ;;\n"
            + "esac\n"
            + "cat > "
            + shell(captured)
            + "\n"
            + "echo 'Pipeline has been submitted to cluster.'\n"
            + "echo 'Job ID: "
            + JOB_ID
            + "'\n"
            + "exit "
            + submitExitCode
            + "\n";
    Files.writeString(script, content, StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    return script;
  }

  private String shell(Path path) {
    return "'" + path.toString().replace("'", "'\"'\"'") + "'";
  }

  private void json(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
