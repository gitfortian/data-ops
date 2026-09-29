package io.yak.ops.business.dataservice.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalDataServiceRuntimeTest {

  @Test
  void cacheReusesResultForSameRuntimeKey() {
    LocalDataServiceRuntime runtime = new LocalDataServiceRuntime();
    RuntimePolicy policy = new RuntimePolicy(true, 60, 20, false, 5, 30);
    AtomicInteger loads = new AtomicInteger();

    DataServiceQueryResponse first = runtime.execute(1L, policy, "key", () -> response(loads.incrementAndGet()));
    DataServiceQueryResponse second = runtime.execute(1L, policy, "key", () -> response(loads.incrementAndGet()));

    assertThat(loads).hasValue(1);
    assertThat(first.rows()).isEqualTo(second.rows());
    assertThat(runtime.snapshot(1L, policy).cacheHits()).isEqualTo(1L);
  }

  @Test
  void runtimeGenerationParticipatesInCacheIdentity() {
    LocalDataServiceRuntime runtime = new LocalDataServiceRuntime();

    String revisionOne = runtime.cacheKey("api=1|revision=10:1", "select ?", List.of(7));
    String revisionTwo = runtime.cacheKey("api=1|revision=11:2", "select ?", List.of(7));

    assertThat(revisionOne).isNotEqualTo(revisionTwo);
    assertThat(revisionOne)
        .isEqualTo(runtime.cacheKey("api=1|revision=10:1", "select ?", List.of(7)));
  }

  @Test
  void circuitOpensAfterConfiguredFailures() {
    LocalDataServiceRuntime runtime = new LocalDataServiceRuntime();
    RuntimePolicy policy = new RuntimePolicy(false, 60, 20, true, 1, 30);

    assertThatThrownBy(() -> runtime.execute(2L, policy, "x", () -> {
      throw new IllegalStateException("db down");
    })).isInstanceOf(IllegalStateException.class);

    assertThatThrownBy(() -> runtime.execute(2L, policy, "y", () -> response(1)))
        .isInstanceOf(DataServiceCircuitOpenException.class);
    assertThat(runtime.snapshot(2L, policy).circuitState()).isEqualTo("OPEN");
  }

  private DataServiceQueryResponse response(int value) {
    return new DataServiceQueryResponse(
        List.of("value"),
        List.of(Map.<String, Object>of("value", value)),
        false,
        1,
        1L);
  }
}
