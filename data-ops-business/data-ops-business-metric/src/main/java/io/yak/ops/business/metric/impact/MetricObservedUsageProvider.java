package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.domain.Metric;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Optional read-side projection of runtime usage owned by external consuming domains.
 *
 * <p>This SPI never makes Metric the owner of Dataset/Data Service/Consumption evidence. Providers
 * project their existing truth into the Metric impact context using stable identities only.
 */
public interface MetricObservedUsageProvider {

  String providerId();

  Coverage observe(Metric metric);

  enum CoverageStatus {
    READY,
    EMPTY,
    UNAVAILABLE,
    FORBIDDEN,
    NOT_APPLICABLE
  }

  record Coverage(
      String provider,
      CoverageStatus status,
      List<Evidence> evidence,
      String reason) {

    public Coverage {
      evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static Coverage notApplicable(String provider, String reason) {
      return new Coverage(provider, CoverageStatus.NOT_APPLICABLE, List.of(), reason);
    }

    public static Coverage unavailable(String provider, String reason) {
      return new Coverage(provider, CoverageStatus.UNAVAILABLE, List.of(), reason);
    }
  }

  record Evidence(
      String evidenceId,
      String productKey,
      String consumerRef,
      String action,
      String outcome,
      LocalDateTime observedAt) {}
}
