package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.repository.DataServiceRateLimitRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cluster-wide per-key fixed-window limiter backed by shared persistence.
 *
 * <p>The API Key policy remains persisted on the key aggregate; this role only coordinates the
 * current minute usage across Yak Ops instances. Old-window cleanup is owned by scheduled
 * observability maintenance, never by the latency-sensitive invocation path.
 */
@Component
public class DataServiceRateLimiter {

  private final DataServiceRateLimitRepository repository;
  private final Clock clock;

  @Autowired
  public DataServiceRateLimiter(DataServiceRateLimitRepository repository) {
    this(repository, Clock.systemUTC());
  }

  DataServiceRateLimiter(DataServiceRateLimitRepository repository, Clock clock) {
    this.repository = Objects.requireNonNull(repository, "repository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public void acquire(DataServiceApiKey key) {
    Objects.requireNonNull(key, "api key");
    long currentMinute = clock.instant().getEpochSecond() / 60L;
    boolean admitted = repository.tryAcquire(
        key.id(), currentMinute, key.rateLimitPerMinute());
    if (!admitted) {
      throw new DataServiceRateLimitException(
          "API Key 已超过每分钟 " + key.rateLimitPerMinute() + " 次调用限制",
          key.id(), key.name(), key.keyPrefix());
    }
  }

  public void invalidate(Long keyId) {
    repository.deleteForKey(keyId);
  }
}
