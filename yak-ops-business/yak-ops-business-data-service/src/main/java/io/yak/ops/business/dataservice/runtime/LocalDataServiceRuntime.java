package io.yak.ops.business.dataservice.runtime;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.yak.ops.business.dataservice.domain.DataServiceQueryResponse;
import io.yak.ops.business.dataservice.domain.RuntimePolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Single-node cache, circuit breaker and runtime metrics. It owns no persisted business truth. */
@Component
public class LocalDataServiceRuntime {

  private static final int DURATION_SAMPLE_SIZE = 256;
  private final Clock clock;
  private final ConcurrentHashMap<Long, RuntimeState> states = new ConcurrentHashMap<>();

  public LocalDataServiceRuntime() {
    this(Clock.systemUTC());
  }

  LocalDataServiceRuntime(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public DataServiceQueryResponse execute(
      Long apiId,
      RuntimePolicy policy,
      String cacheKey,
      Supplier<DataServiceQueryResponse> loader) {
    if (apiId == null) throw new IllegalArgumentException("数据服务 Runtime 缺少 API ID");
    RuntimePolicy effective = Objects.requireNonNull(policy, "runtime policy");
    RuntimeState state = states.computeIfAbsent(apiId, ignored -> new RuntimeState());
    state.ensurePolicy(effective);
    if (!effective.cacheEnabled()) return executeProtected(state, effective, loader);

    long requestStarted = System.nanoTime();
    DataServiceQueryResponse cached = state.cache().getIfPresent(cacheKey);
    if (cached != null) {
      long durationMs = elapsedMs(requestStarted);
      state.metrics.recordSuccess(durationMs, true, clock.instant());
      return copyWithDuration(cached, durationMs);
    }

    AtomicBoolean loadedByThisCall = new AtomicBoolean(false);
    DataServiceQueryResponse response = state.cache().get(cacheKey, ignored -> {
      loadedByThisCall.set(true);
      return executeProtected(state, effective, loader);
    });
    if (!loadedByThisCall.get()) {
      long durationMs = elapsedMs(requestStarted);
      state.metrics.recordSuccess(durationMs, true, clock.instant());
      return copyWithDuration(response, durationMs);
    }
    return response;
  }

  /**
   * Cache identity includes a persisted runtime namespace so a republish/settings generation cannot
   * accidentally reuse an older node-local cache entry when cross-node invalidation is unavailable.
   */
  public String cacheKey(String namespace, String compiledSql, List<Object> bindings) {
    StringBuilder raw = new StringBuilder(namespace == null ? "" : namespace)
        .append('\u001d')
        .append(compiledSql == null ? "" : compiledSql)
        .append('\u001f');
    if (bindings != null) {
      for (Object value : bindings) {
        raw.append(value == null ? "<null>" : value.getClass().getName() + ':' + value).append('\u001e');
      }
    }
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("无法生成数据服务缓存 Key", exception);
    }
  }

  /** Compatibility overload for callers/tests that do not yet supply a runtime generation. */
  public String cacheKey(String compiledSql, List<Object> bindings) {
    return cacheKey("", compiledSql, bindings);
  }

  public DataServiceRuntimeSnapshot snapshot(Long apiId, RuntimePolicy policy) {
    if (apiId == null) throw new IllegalArgumentException("数据服务 Runtime 缺少 API ID");
    RuntimeState state = states.computeIfAbsent(apiId, ignored -> new RuntimeState());
    state.ensurePolicy(policy);
    return state.snapshot(apiId, policy, clock.instant());
  }

  public void invalidate(Long apiId) {
    RuntimeState state = apiId == null ? null : states.get(apiId);
    if (state != null) state.invalidateOperationalState();
  }

  public void remove(Long apiId) {
    if (apiId != null) states.remove(apiId);
  }

  private DataServiceQueryResponse executeProtected(
      RuntimeState state, RuntimePolicy policy, Supplier<DataServiceQueryResponse> loader) {
    CircuitDecision decision = state.circuit.beforeCall(policy, clock.instant());
    if (!decision.allowed()) {
      state.metrics.recordCircuitRejected(clock.instant());
      throw new DataServiceCircuitOpenException(
          "数据服务下游暂时不可用，熔断保护中，请在 " + decision.retryAfterSeconds() + " 秒后重试");
    }
    long started = System.nanoTime();
    try {
      DataServiceQueryResponse response = loader.get();
      long durationMs = elapsedMs(started);
      state.circuit.onSuccess();
      state.metrics.recordSuccess(durationMs, false, clock.instant());
      return copyWithDuration(response, durationMs);
    } catch (RuntimeException exception) {
      long durationMs = elapsedMs(started);
      state.circuit.onFailure(policy, clock.instant());
      state.metrics.recordFailure(durationMs, clock.instant());
      throw exception;
    }
  }

  private DataServiceQueryResponse copyWithDuration(DataServiceQueryResponse response, long durationMs) {
    return new DataServiceQueryResponse(
        response.columns(), response.rows(), response.truncated(), response.rowCount(), durationMs,
        response.totalNum(), response.pageNum(), response.pageSize());
  }

  private long elapsedMs(long started) {
    return Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
  }

  private static final class RuntimeState {
    private volatile RuntimePolicy policy;
    private volatile Cache<String, DataServiceQueryResponse> cache;
    private final CircuitBreaker circuit = new CircuitBreaker();
    private final RuntimeMetrics metrics = new RuntimeMetrics();

    synchronized void ensurePolicy(RuntimePolicy next) {
      Objects.requireNonNull(next, "runtime policy");
      if (Objects.equals(policy, next) && cache != null) return;
      RuntimePolicy previous = policy;
      policy = next;
      if (cache != null) cache.invalidateAll();
      cache = Caffeine.newBuilder()
          .maximumSize(next.cacheMaxEntries())
          .expireAfterWrite(Duration.ofSeconds(next.cacheTtlSeconds()))
          .build();
      if (previous != null
          && (previous.circuitBreakerEnabled() != next.circuitBreakerEnabled()
              || previous.failureThreshold() != next.failureThreshold()
              || previous.recoverySeconds() != next.recoverySeconds())) {
        circuit.reset();
      }
    }

    Cache<String, DataServiceQueryResponse> cache() { return cache; }

    synchronized void invalidateOperationalState() {
      if (cache != null) cache.invalidateAll();
      circuit.reset();
    }

    DataServiceRuntimeSnapshot snapshot(Long apiId, RuntimePolicy policy, Instant now) {
      RuntimeMetrics.MetricSnapshot metric = metrics.snapshot();
      CircuitBreaker.CircuitSnapshot breaker = circuit.snapshot(policy, now);
      long entries = cache == null ? 0L : cache.estimatedSize();
      return new DataServiceRuntimeSnapshot(
          apiId, policy.cacheEnabled(), policy.cacheTtlSeconds(), policy.cacheMaxEntries(), entries,
          policy.circuitBreakerEnabled(), policy.failureThreshold(), policy.recoverySeconds(),
          breaker.state(), breaker.openUntil(), metric.totalCalls(), metric.successCalls(),
          metric.failureCalls(), metric.cacheHits(), metric.circuitRejected(), metric.successRate(),
          metric.cacheHitRate(), metric.averageDurationMs(), metric.p95DurationMs(),
          metric.lastSuccessAt(), metric.lastFailureAt(), "LOCAL");
    }
  }

  private static final class CircuitBreaker {
    private int consecutiveFailures;
    private Instant openUntil;
    private boolean halfOpenProbeInFlight;

    synchronized CircuitDecision beforeCall(RuntimePolicy policy, Instant now) {
      if (!policy.circuitBreakerEnabled()) return new CircuitDecision(true, 0L);
      if (openUntil == null) return new CircuitDecision(true, 0L);
      if (now.isBefore(openUntil)) {
        return new CircuitDecision(false, Math.max(1L, Duration.between(now, openUntil).toSeconds()));
      }
      if (halfOpenProbeInFlight) return new CircuitDecision(false, 1L);
      halfOpenProbeInFlight = true;
      return new CircuitDecision(true, 0L);
    }

    synchronized void onSuccess() {
      consecutiveFailures = 0; openUntil = null; halfOpenProbeInFlight = false;
    }

    synchronized void onFailure(RuntimePolicy policy, Instant now) {
      if (!policy.circuitBreakerEnabled()) return;
      halfOpenProbeInFlight = false;
      consecutiveFailures++;
      if (openUntil != null || consecutiveFailures >= policy.failureThreshold()) {
        openUntil = now.plusSeconds(policy.recoverySeconds());
      }
    }

    synchronized void reset() {
      consecutiveFailures = 0; openUntil = null; halfOpenProbeInFlight = false;
    }

    synchronized CircuitSnapshot snapshot(RuntimePolicy policy, Instant now) {
      if (!policy.circuitBreakerEnabled()) return new CircuitSnapshot("DISABLED", null);
      if (openUntil == null) return new CircuitSnapshot("CLOSED", null);
      if (now.isBefore(openUntil)) return new CircuitSnapshot("OPEN", openUntil);
      return new CircuitSnapshot("HALF_OPEN", openUntil);
    }

    record CircuitSnapshot(String state, Instant openUntil) {}
  }

  private static final class RuntimeMetrics {
    private final AtomicLong totalCalls = new AtomicLong();
    private final AtomicLong successCalls = new AtomicLong();
    private final AtomicLong failureCalls = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong circuitRejected = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();
    private final ArrayDeque<Long> durations = new ArrayDeque<>();
    private volatile Instant lastSuccessAt;
    private volatile Instant lastFailureAt;

    void recordSuccess(long durationMs, boolean cacheHit, Instant now) {
      totalCalls.incrementAndGet(); successCalls.incrementAndGet();
      if (cacheHit) cacheHits.incrementAndGet();
      totalDurationMs.addAndGet(Math.max(0L, durationMs)); lastSuccessAt = now; addDuration(durationMs);
    }

    void recordFailure(long durationMs, Instant now) {
      totalCalls.incrementAndGet(); failureCalls.incrementAndGet();
      totalDurationMs.addAndGet(Math.max(0L, durationMs)); lastFailureAt = now; addDuration(durationMs);
    }

    void recordCircuitRejected(Instant now) {
      totalCalls.incrementAndGet(); failureCalls.incrementAndGet(); circuitRejected.incrementAndGet();
      lastFailureAt = now; addDuration(0L);
    }

    private synchronized void addDuration(long durationMs) {
      if (durations.size() >= DURATION_SAMPLE_SIZE) durations.removeFirst();
      durations.addLast(Math.max(0L, durationMs));
    }

    synchronized MetricSnapshot snapshot() {
      long total = totalCalls.get();
      long success = successCalls.get();
      long hits = cacheHits.get();
      List<Long> sorted = new ArrayList<>(durations);
      sorted.sort(Long::compareTo);
      long p95 = sorted.isEmpty() ? 0L
          : sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * 0.95) - 1));
      long average = total == 0 ? 0L : totalDurationMs.get() / total;
      return new MetricSnapshot(
          total, success, failureCalls.get(), hits, circuitRejected.get(),
          total == 0 ? 1D : (double) success / total,
          total == 0 ? 0D : (double) hits / total,
          average, p95, lastSuccessAt, lastFailureAt);
    }

    record MetricSnapshot(
        long totalCalls, long successCalls, long failureCalls, long cacheHits, long circuitRejected,
        double successRate, double cacheHitRate, long averageDurationMs, long p95DurationMs,
        Instant lastSuccessAt, Instant lastFailureAt) {}
  }

  private record CircuitDecision(boolean allowed, long retryAfterSeconds) {}
}
