package io.yak.ops.business.dataset.observability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Operational knobs for persisted Dataset Query Runtime diagnostics. */
@Data
@Component
@ConfigurationProperties(prefix = "yak.dataset.query-observability")
public class DatasetQueryObservabilityProperties {

  private int retentionDays = 7;
  private int cleanupIntervalMinutes = 60;
  private int cleanupBatchSize = 1000;
}
