package io.yak.ops.business.sync.realtime.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironment.RuntimeConfig;
import io.yak.ops.business.sync.realtime.domain.ComputeEnvironmentSnapshot;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlinkJobDiscoveryClientTest {

  private HttpServer server;
  private FlinkJobDiscoveryClient client;
  private ComputeEnvironmentSnapshot environment;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.start();
    String restUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    RealtimeSyncProperties properties = new RealtimeSyncProperties();
    client = new FlinkJobDiscoveryClient(HttpClient.newHttpClient(), new ObjectMapper(), properties);
    environment =
        new ComputeEnvironmentSnapshot(
            3L,
            "test-env",
            ComputeEnvironment.ENGINE_FLINK_CDC,
            ComputeEnvironment.DEPLOYMENT_REMOTE,
            ComputeEnvironment.SUBMITTER_LOCAL,
            new RuntimeConfig(restUrl, "/opt/flink", "/opt/flink-cdc", null, "1.20.5", "3.6.0"),
            1);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void findsOnlyExactRuntimeJobName() {
    server.createContext(
        "/jobs/overview",
        exchange -> {
          byte[] body =
              ("{\"jobs\":["
                      + "{\"jid\":\"0123456789abcdef0123456789abcdef\",\"name\":\"yak-rt-target\"},"
                      + "{\"jid\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"name\":\"yak-rt-target-old\"},"
                      + "{\"jid\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\",\"name\":\"yak-rt-target\"}"
                      + "]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });

    List<String> ids = client.findJobIds(environment, "yak-rt-target");

    assertThat(ids)
        .containsExactly(
            "0123456789abcdef0123456789abcdef",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
  }
}
