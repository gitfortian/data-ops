package io.yak.ops.business.dataservice.observability;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.dataservice.repository.DataServiceObservabilityMaintenanceRepository;
import io.yak.ops.business.dataservice.repository.DataServiceRateLimitRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DataServiceObservabilityMaintenanceTest {

  @Test
  void rollsOnlyBoundedFullyExpiredHoursThenExpiresRollupsAndRateWindows() {
    DataServiceObservabilityMaintenanceRepository repository =
        mock(DataServiceObservabilityMaintenanceRepository.class);
    DataServiceRateLimitRepository rateRepository = mock(DataServiceRateLimitRepository.class);
    DataServiceObservabilityMaintenance maintenance =
        new DataServiceObservabilityMaintenance(repository, rateRepository, 30, 365, 2);
    LocalDateTime now = LocalDateTime.of(2026, 8, 28, 4, 50);
    LocalDateTime first = LocalDateTime.of(2026, 6, 1, 1, 0);
    LocalDateTime second = LocalDateTime.of(2026, 6, 1, 2, 0);
    LocalDateTime rawCutoff = now.minusDays(30).truncatedTo(ChronoUnit.HOURS);

    when(repository.oldestRawHourBefore(rawCutoff))
        .thenReturn(Optional.of(first), Optional.of(second));
    when(repository.rollupAndDeleteHour(first)).thenReturn(12);
    when(repository.rollupAndDeleteHour(second)).thenReturn(8);

    maintenance.maintainAt(now);

    InOrder order = inOrder(repository);
    order.verify(repository).oldestRawHourBefore(rawCutoff);
    order.verify(repository).rollupAndDeleteHour(first);
    order.verify(repository).oldestRawHourBefore(rawCutoff);
    order.verify(repository).rollupAndDeleteHour(second);
    verify(repository).deleteRollupsBefore(now.minusDays(365));
    verify(rateRepository).deleteBefore(
        now.atZone(ZoneId.systemDefault()).toEpochSecond() / 60L - 2L);
  }
}
