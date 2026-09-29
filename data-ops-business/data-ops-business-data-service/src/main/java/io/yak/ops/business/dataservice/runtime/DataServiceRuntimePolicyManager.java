package io.yak.ops.business.dataservice.runtime;

import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import io.yak.ops.business.dataservice.query.DataServiceReader;
import io.yak.ops.business.dataservice.repository.DataServiceRepository;
import io.yak.ops.business.dataservice.repository.DataServiceRuntimeMetricsRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceRuntimePolicyManager {

  private static final int P95_SAMPLE_SIZE = 256;
  private final DataServiceReader reader;
  private final DataServiceRepository repository;
  private final LocalDataServiceRuntime runtime;
  private final DataServiceRuntimeMetricsRepository metricsRepository;

  public DataServiceRuntimeSnapshot snapshot(Long apiId) {
    DataServiceDefinition definition = reader.require(apiId);
    return clusterSnapshot(definition);
  }

  @Transactional
  public DataServiceRuntimeSnapshot update(Long apiId, RuntimePolicyInput input) {
    DataServiceDefinition definition = reader.require(apiId);
    RuntimePolicy policy = normalize(input);
    definition.updateRuntimePolicy(policy, LocalDateTime.now());
    DataServiceDefinition saved = repository.save(definition);
    runtime.invalidate(apiId);
    return clusterSnapshot(saved);
  }

  public void invalidate(Long apiId) { runtime.invalidate(apiId); }
  public void remove(Long apiId) { runtime.remove(apiId); }

  private DataServiceRuntimeSnapshot clusterSnapshot(DataServiceDefinition definition) {
    DataServiceRuntimeSnapshot local = runtime.snapshot(definition.id(), definition.runtimePolicy());
    DataServiceRuntimeMetricsRepository.Metrics metrics =
        metricsRepository.load(definition.id(), P95_SAMPLE_SIZE);
    long total = metrics.totalCalls();
    long success = metrics.successCalls();
    long average = total == 0L ? 0L : metrics.totalDurationMs() / total;
    double successRate = total == 0L ? 1D : (double) success / total;

    return new DataServiceRuntimeSnapshot(
        local.apiId(),
        local.cacheEnabled(),
        local.cacheTtlSeconds(),
        local.cacheMaxEntries(),
        local.cacheEntries(),
        local.circuitBreakerEnabled(),
        local.failureThreshold(),
        local.recoverySeconds(),
        local.circuitState(),
        local.circuitOpenUntil(),
        total,
        success,
        metrics.failureCalls(),
        local.cacheHits(),
        local.circuitRejected(),
        successRate,
        local.cacheHitRate(),
        average,
        p95(metrics.recentDurationsMs()),
        metrics.lastSuccessAt(),
        metrics.lastFailureAt(),
        "CLUSTER_INVOCATION_LOCAL_RESILIENCE");
  }

  private long p95(List<Long> durations) {
    if (durations == null || durations.isEmpty()) return 0L;
    List<Long> sorted = new ArrayList<>(durations);
    sorted.sort(Long::compareTo);
    int index = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95D) - 1);
    return sorted.get(Math.max(0, index));
  }

  private RuntimePolicy normalize(RuntimePolicyInput input) {
    if (input == null) throw new IllegalArgumentException("Runtime 配置不能为空");
    return new RuntimePolicy(
        Boolean.TRUE.equals(input.cacheEnabled()),
        range(input.cacheTtlSeconds(), 60, 1, 3_600, "缓存 TTL"),
        range(input.cacheMaxEntries(), 200, 1, 5_000, "缓存最大条目数"),
        Boolean.TRUE.equals(input.circuitBreakerEnabled()),
        range(input.failureThreshold(), 5, 1, 20, "熔断失败阈值"),
        range(input.recoverySeconds(), 30, 1, 300, "熔断恢复时间"));
  }

  private int range(Integer value, int fallback, int min, int max, String name) {
    int normalized = value == null ? fallback : value;
    if (normalized < min || normalized > max) {
      throw new IllegalArgumentException(name + " 必须在 " + min + "~" + max + " 之间");
    }
    return normalized;
  }
}
