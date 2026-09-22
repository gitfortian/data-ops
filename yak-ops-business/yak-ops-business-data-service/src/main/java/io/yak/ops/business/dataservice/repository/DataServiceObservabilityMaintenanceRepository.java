package io.yak.ops.business.dataservice.repository;

import java.time.LocalDateTime;
import java.util.Optional;

/** Persistence boundary for raw invocation retention and hourly rollup maintenance. */
public interface DataServiceObservabilityMaintenanceRepository {

  Optional<LocalDateTime> oldestRawHourBefore(LocalDateTime cutoff);

  /** Rolls one closed UTC/local-database hour into aggregate storage and deletes the raw rows atomically. */
  int rollupAndDeleteHour(LocalDateTime bucketStart);

  int deleteRollupsBefore(LocalDateTime cutoff);
}
