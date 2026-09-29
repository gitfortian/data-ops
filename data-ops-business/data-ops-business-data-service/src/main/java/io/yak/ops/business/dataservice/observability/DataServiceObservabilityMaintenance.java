package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.repository.DataServiceObservabilityMaintenanceRepository;
import io.yak.ops.business.dataservice.repository.DataServiceRateLimitRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded lifecycle maintenance for invocation evidence and shared minute windows. */
@Slf4j
@Component
@ConditionalOnDataSourceEnabled
public class DataServiceObservabilityMaintenance {

  private final DataServiceObservabilityMaintenanceRepository repository;
  private final DataServiceRateLimitRepository rateLimitRepository;
  private final int rawRetentionDays;
  private final int rollupRetentionDays;
  private final int maxHourlyBucketsPerRun;

  public DataServiceObservabilityMaintenance(
      DataServiceObservabilityMaintenanceRepository repository,
      DataServiceRateLimitRepository rateLimitRepository,
      @Value("${yak.data-service.observability.raw-retention-days:30}") int rawRetentionDays,
      @Value("${yak.data-service.observability.rollup-retention-days:365}") int rollupRetentionDays,
      @Value("${yak.data-service.observability.max-hourly-buckets-per-run:168}")
          int maxHourlyBucketsPerRun) {
    this.repository = repository;
    this.rateLimitRepository = rateLimitRepository;
    this.rawRetentionDays = Math.max(1, rawRetentionDays);
    this.rollupRetentionDays = Math.max(this.rawRetentionDays, rollupRetentionDays);
    this.maxHourlyBucketsPerRun = Math.max(1, Math.min(744, maxHourlyBucketsPerRun));
  }

  @Scheduled(cron = "${yak.data-service.observability.maintenance-cron:0 20 3 * * *}")
  public void maintain() {
    maintainAt(LocalDateTime.now());
  }

  void maintainAt(LocalDateTime now) {
    // Only roll a complete hour whose end is outside the raw-retention window. Keeping the
    // boundary hour raw avoids dropping still-retained rows from 30d overview queries.
    LocalDateTime rawCutoff = now.minusDays(rawRetentionDays).truncatedTo(ChronoUnit.HOURS);
    int buckets = 0;
    int rawRows = 0;
    while (buckets < maxHourlyBucketsPerRun) {
      var oldest = repository.oldestRawHourBefore(rawCutoff);
      if (oldest.isEmpty()) break;
      rawRows += repository.rollupAndDeleteHour(oldest.get());
      buckets++;
    }

    int expiredRollups = repository.deleteRollupsBefore(now.minusDays(rollupRetentionDays));
    long minuteFloor = now.atZone(ZoneId.systemDefault()).toEpochSecond() / 60L;
    int expiredRateWindows = rateLimitRepository.deleteBefore(minuteFloor - 2L);

    if (buckets > 0 || expiredRollups > 0 || expiredRateWindows > 0) {
      log.info(
          "Data Service observability maintenance complete: hourlyBuckets={}, rawRows={}, expiredRollups={}, expiredRateWindows={}",
          buckets,
          rawRows,
          expiredRollups,
          expiredRateWindows);
    }
  }
}
