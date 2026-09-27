package io.yak.ops.business.metric.impact;

import io.yak.ops.business.metric.domain.Metric;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Optional read-side projection from a Metric reference to an existing governed Consumption target.
 *
 * <p>Metric never creates or owns the target. Providers verify a stable owning-domain reference and
 * return the canonical ProductKey/href plus truthful resolution state. No METRIC ProductType is
 * introduced.
 */
public interface MetricConsumptionTargetProvider {

  String providerId();

  List<TargetResolution> resolve(Metric metric);

  enum TargetStatus {
    FOUND,
    NOT_FOUND,
    NOT_DISCOVERABLE,
    FORBIDDEN,
    UNAVAILABLE,
    NOT_APPLICABLE
  }

  record TargetResolution(
      String provider,
      Long referenceId,
      String referenceType,
      Long referenceObjectId,
      TargetStatus status,
      String productKey,
      String canonicalHref,
      String reason,
      LocalDateTime resolvedAt) {

    public static TargetResolution notApplicable(String provider, String reason) {
      return new TargetResolution(
          provider,
          null,
          null,
          null,
          TargetStatus.NOT_APPLICABLE,
          null,
          null,
          reason,
          LocalDateTime.now());
    }

    public static TargetResolution unavailable(
        String provider,
        Long referenceId,
        String referenceType,
        Long referenceObjectId,
        String productKey,
        String reason) {
      return new TargetResolution(
          provider,
          referenceId,
          referenceType,
          referenceObjectId,
          TargetStatus.UNAVAILABLE,
          productKey,
          null,
          reason,
          LocalDateTime.now());
    }
  }
}
