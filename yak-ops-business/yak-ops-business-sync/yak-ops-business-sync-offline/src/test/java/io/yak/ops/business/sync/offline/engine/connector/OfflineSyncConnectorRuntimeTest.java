package io.yak.ops.business.sync.offline.engine.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRuntimeProfileVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineConnectorRuntimeSnapshotVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfflineSyncConnectorRuntimeTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void mergesCurrentProfilesWithLiveWorkerSchemas() throws Exception {
    LinkUpConnectorDiscoveryClient client = mock(LinkUpConnectorDiscoveryClient.class);
    when(client.schemas()).thenReturn(List.of(source(), sink(), starRocksSource()));

    OfflineSyncConnectorRuntime runtime = new OfflineSyncConnectorRuntime(client, 60_000L);
    OfflineConnectorRuntimeSnapshotVO result = runtime.snapshot();

    assertThat(result.getReachable()).isTrue();
    assertThat(result.getStale()).isFalse();
    assertThat(result.getConnectors()).hasSize(3);
    assertThat(result.getConnectors())
        .anySatisfy(
            connector -> {
              assertThat(connector.getConnectorId()).isEqualTo("starrocks");
              assertThat(connector.getRole()).isEqualTo("SOURCE");
              assertThat(connector.getAvailable()).isTrue();
            });

    OfflineConnectorRuntimeProfileVO mysql =
        result.getProfiles().stream()
            .filter(profile -> "MYSQL".equals(profile.getDbType()))
            .findFirst()
            .orElseThrow();
    assertThat(mysql.getSource().getAvailable()).isTrue();
    assertThat(mysql.getSource().getSchemaFingerprint()).isEqualTo("source-fp");
    assertThat(mysql.getSource().getCapabilities())
        .containsExactly("CUSTOM_SQL", "MULTI_TABLE");
    assertThat(mysql.getSink().getAvailable()).isTrue();
    assertThat(mysql.getSink().getCapabilities())
        .containsExactly("AUTO_CREATE_TABLE", "UPSERT");
  }

  @Test
  void initialDiscoveryFailureIsUnavailableButNotStale() {
    LinkUpConnectorDiscoveryClient client = mock(LinkUpConnectorDiscoveryClient.class);
    when(client.schemas())
        .thenThrow(new LinkUpConnectorDiscoveryClient.DiscoveryException(503, "worker down"));

    OfflineSyncConnectorRuntime runtime = new OfflineSyncConnectorRuntime(client, 0L);
    OfflineConnectorRuntimeSnapshotVO result = runtime.snapshot();

    assertThat(result.getReachable()).isFalse();
    assertThat(result.getStale()).isFalse();
    assertThat(result.getSyncedAtMillis()).isNull();
    assertThat(result.getConnectors()).isEmpty();
    assertThat(result.getProfiles())
        .allSatisfy(
            profile -> {
              assertThat(profile.getSource().getAvailable()).isFalse();
              assertThat(profile.getSink().getAvailable()).isFalse();
            });
  }

  @Test
  void staleSchemaMetadataNeverPretendsWorkerIsCurrentlyAvailable() throws Exception {
    LinkUpConnectorDiscoveryClient client = mock(LinkUpConnectorDiscoveryClient.class);
    when(client.schemas())
        .thenReturn(List.of(source(), sink()))
        .thenThrow(new LinkUpConnectorDiscoveryClient.DiscoveryException(503, "worker down"));

    OfflineSyncConnectorRuntime runtime = new OfflineSyncConnectorRuntime(client, 0L);
    OfflineConnectorRuntimeSnapshotVO live = runtime.snapshot();
    OfflineConnectorRuntimeSnapshotVO stale = runtime.snapshot();

    assertThat(live.getReachable()).isTrue();
    assertThat(stale.getReachable()).isFalse();
    assertThat(stale.getStale()).isTrue();
    assertThat(stale.getErrorMessage()).contains("worker down");

    OfflineConnectorRuntimeProfileVO mysql =
        stale.getProfiles().stream()
            .filter(profile -> "MYSQL".equals(profile.getDbType()))
            .findFirst()
            .orElseThrow();
    assertThat(mysql.getSource().getAvailable()).isFalse();
    assertThat(mysql.getSource().getSchemaFingerprint()).isEqualTo("source-fp");
    assertThat(mysql.getSink().getAvailable()).isFalse();
    assertThat(stale.getConnectors()).allSatisfy(item -> assertThat(item.getAvailable()).isFalse());
  }

  private JsonNode source() throws Exception {
    return objectMapper.readTree(
        "{\"connectorId\":\"jdbc\",\"role\":\"SOURCE\","
            + "\"schemaVersion\":\"1\",\"schemaFingerprint\":\"source-fp\","
            + "\"implementationVersion\":\"1.0.0\","
            + "\"capabilities\":[\"MULTI_TABLE\",\"CUSTOM_SQL\"]}");
  }

  private JsonNode sink() throws Exception {
    return objectMapper.readTree(
        "{\"connectorId\":\"jdbc\",\"role\":\"SINK\","
            + "\"schemaVersion\":\"1\",\"schemaFingerprint\":\"sink-fp\","
            + "\"implementationVersion\":\"1.0.0\","
            + "\"capabilities\":[\"UPSERT\",\"AUTO_CREATE_TABLE\"]}");
  }

  private JsonNode starRocksSource() throws Exception {
    return objectMapper.readTree(
        "{\"connectorId\":\"starrocks\",\"role\":\"SOURCE\","
            + "\"schemaVersion\":\"1\",\"schemaFingerprint\":\"starrocks-fp\","
            + "\"capabilities\":[\"MULTI_TABLE\",\"PARTITION_SPLIT\"]}");
  }
}
