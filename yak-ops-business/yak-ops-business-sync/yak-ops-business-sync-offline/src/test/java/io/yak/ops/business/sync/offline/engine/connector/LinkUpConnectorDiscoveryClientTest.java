package io.yak.ops.business.sync.offline.engine.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.yak.ops.business.sync.offline.config.OfflineSyncProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkUpConnectorDiscoveryClientTest {

  private HttpServer server;
  private LinkUpConnectorDiscoveryClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/connectors", this::connectors);
    server.start();

    OfflineSyncProperties properties = new OfflineSyncProperties();
    properties.getEngine().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    client =
        new LinkUpConnectorDiscoveryClient(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            properties);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void listsSchemasFromLoadedWorkerFactories() {
    List<JsonNode> schemas = client.schemas();

    assertThat(schemas).hasSize(2);
    assertThat(schemas.get(0).path("connectorId").asText()).isEqualTo("jdbc");
    assertThat(schemas.get(0).path("role").asText()).isEqualTo("SOURCE");
    assertThat(schemas.get(0).path("capabilities").get(0).asText())
        .isEqualTo("MULTI_TABLE");
  }

  @Test
  void fetchesOneFullSchemaWithExplicitRole() {
    JsonNode schema = client.schema("jdbc", "source");

    assertThat(schema.path("connectorId").asText()).isEqualTo("jdbc");
    assertThat(schema.path("role").asText()).isEqualTo("SOURCE");
    assertThat(schema.path("schemaFingerprint").asText()).isEqualTo("source-fp");
  }

  private void connectors(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    if (path.endsWith("/jdbc/schema")) {
      assertThat(exchange.getRequestURI().getQuery()).isEqualTo("role=SOURCE");
      json(exchange, sourceSchema());
      return;
    }
    json(exchange, "[" + sourceSchema() + "," + sinkSchema() + "]");
  }

  private String sourceSchema() {
    return "{\"connectorId\":\"jdbc\",\"role\":\"SOURCE\","
        + "\"schemaVersion\":\"1\",\"schemaFingerprint\":\"source-fp\","
        + "\"implementationVersion\":\"1.0.0\","
        + "\"capabilities\":[\"MULTI_TABLE\",\"CUSTOM_SQL\"],"
        + "\"options\":[],\"rules\":[]}";
  }

  private String sinkSchema() {
    return "{\"connectorId\":\"jdbc\",\"role\":\"SINK\","
        + "\"schemaVersion\":\"1\",\"schemaFingerprint\":\"sink-fp\","
        + "\"implementationVersion\":\"1.0.0\","
        + "\"capabilities\":[\"UPSERT\",\"AUTO_CREATE_TABLE\"],"
        + "\"options\":[],\"rules\":[]}";
  }

  private void json(HttpExchange exchange, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
