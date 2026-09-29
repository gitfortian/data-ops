package io.yak.ops.business.dataservice.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.DataServiceSettings;
import io.yak.ops.business.dataservice.domain.PublishedRuntimeSnapshot;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.domain.SourceReference;
import io.yak.ops.business.dataservice.domain.access.AuthMode;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.dataservice.repository.DataServiceRuntimeMetricsRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataServiceRuntimePolicyManagerTest {

  @Test
  void snapshotUsesClusterInvocationTotalsButKeepsNodeLocalCacheAndCircuitEvidence() {
    DataServiceReader reader = mock(DataServiceReader.class);
    DataServiceRepository repository = mock(DataServiceRepository.class);
    LocalDataServiceRuntime localRuntime = mock(LocalDataServiceRuntime.class);
    DataServiceRuntimeMetricsRepository metricsRepository =
        mock(DataServiceRuntimeMetricsRepository.class);
    DataServiceRuntimePolicyManager manager = new DataServiceRuntimePolicyManager(
        reader, repository, localRuntime, metricsRepository);
    DataServiceDefinition definition = definition();
    when(reader.require(7L)).thenReturn(definition);
    when(localRuntime.snapshot(7L, definition.runtimePolicy())).thenReturn(
        new DataServiceRuntimeSnapshot(
            7L, true, 60, 200, 12L,
            true, 5, 30, "OPEN", Instant.parse("2026-08-28T05:01:00Z"),
            3L, 2L, 1L, 2L, 1L, 0.66D, 0.5D, 4L, 9L,
            Instant.parse("2026-08-28T04:49:00Z"),
            Instant.parse("2026-08-28T04:50:00Z"),
            "LOCAL"));
    when(metricsRepository.load(7L, 256)).thenReturn(
        new DataServiceRuntimeMetricsRepository.Metrics(
            100L, 90L, 10L, 5_000L,
            List.of(10L, 20L, 30L, 40L),
            Instant.parse("2026-08-28T04:58:00Z"),
            Instant.parse("2026-08-28T04:57:00Z")));

    DataServiceRuntimeSnapshot snapshot = manager.snapshot(7L);

    assertThat(snapshot.totalCalls()).isEqualTo(100L);
    assertThat(snapshot.successCalls()).isEqualTo(90L);
    assertThat(snapshot.failureCalls()).isEqualTo(10L);
    assertThat(snapshot.successRate()).isEqualTo(0.9D);
    assertThat(snapshot.averageDurationMs()).isEqualTo(50L);
    assertThat(snapshot.p95DurationMs()).isEqualTo(40L);
    assertThat(snapshot.lastSuccessAt()).isEqualTo(Instant.parse("2026-08-28T04:58:00Z"));

    assertThat(snapshot.cacheEntries()).isEqualTo(12L);
    assertThat(snapshot.cacheHits()).isEqualTo(2L);
    assertThat(snapshot.circuitState()).isEqualTo("OPEN");
    assertThat(snapshot.circuitRejected()).isEqualTo(1L);
    assertThat(snapshot.metricsScope()).isEqualTo("CLUSTER_INVOCATION_LOCAL_RESILIENCE");
  }

  private DataServiceDefinition definition() {
    return DataServiceDefinition.restore(
        7L,
        3L,
        11L,
        new DataServiceSettings("Orders", "/orders", 100, 30, true, null, false),
        new PublishedRuntimeSnapshot(9L, "select id from orders"),
        new SourceReference("TEST", "orders", 101L, 1),
        new RuntimePolicy(true, 60, 200, true, 5, 30),
        AuthMode.NONE,
        LocalDateTime.of(2026, 8, 28, 12, 0),
        LocalDateTime.of(2026, 8, 28, 12, 40));
  }
}
