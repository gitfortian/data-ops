package io.yak.ops.business.dataservice.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.domain.access.DataServiceApiKey;
import io.yak.ops.business.dataservice.repository.DataServiceRateLimitRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DataServiceRateLimiterTest {

  private static final Instant NOW = Instant.parse("2026-08-28T04:50:00Z");

  @Test
  void springCanCreateRateLimiterWithRepositoryConstructor() {
    DataServiceRateLimitRepository repository = mock(DataServiceRateLimitRepository.class);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(DataServiceRateLimitRepository.class, () -> repository);
      context.register(DataServiceRateLimiter.class);
      context.refresh();

      assertThat(context.getBean(DataServiceRateLimiter.class)).isNotNull();
    }
  }

  @Test
  void delegatesAdmissionToSharedRepositoryWindow() {
    DataServiceRateLimitRepository repository = mock(DataServiceRateLimitRepository.class);
    DataServiceRateLimiter limiter = new DataServiceRateLimiter(
        repository, Clock.fixed(NOW, ZoneOffset.UTC));
    DataServiceApiKey key = key(60);
    long minute = NOW.getEpochSecond() / 60L;
    when(repository.tryAcquire(9L, minute, 60)).thenReturn(true);

    limiter.acquire(key);

    verify(repository).tryAcquire(9L, minute, 60);
  }

  @Test
  void rejectsWhenClusterWindowAlreadyReachedTheConfiguredLimit() {
    DataServiceRateLimitRepository repository = mock(DataServiceRateLimitRepository.class);
    DataServiceRateLimiter limiter = new DataServiceRateLimiter(
        repository, Clock.fixed(NOW, ZoneOffset.UTC));
    DataServiceApiKey key = key(2);
    long minute = NOW.getEpochSecond() / 60L;
    when(repository.tryAcquire(9L, minute, 2)).thenReturn(false);

    assertThatThrownBy(() -> limiter.acquire(key))
        .isInstanceOf(DataServiceRateLimitException.class)
        .hasMessageContaining("每分钟 2 次");
  }

  @Test
  void invalidationClearsSharedWindowsForRotatedOrDeletedKey() {
    DataServiceRateLimitRepository repository = mock(DataServiceRateLimitRepository.class);
    DataServiceRateLimiter limiter = new DataServiceRateLimiter(
        repository, Clock.fixed(NOW, ZoneOffset.UTC));

    limiter.invalidate(9L);

    verify(repository).deleteForKey(9L);
  }

  private DataServiceApiKey key(int rateLimit) {
    LocalDateTime time = LocalDateTime.of(2026, 8, 28, 12, 50);
    return new DataServiceApiKey(
        9L, 7L, "consumer-a", "yak_ds_abcd", "hash", true,
        rateLimit, null, null, time, time);
  }
}
