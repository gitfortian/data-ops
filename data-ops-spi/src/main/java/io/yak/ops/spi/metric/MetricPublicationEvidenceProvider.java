package io.yak.ops.spi.metric;

/**
 * Read-only evidence boundary for an immutable Metric publication reference.
 *
 * <p>The Metric module owns the implementation. Callers must not infer publication identity from
 * a MetricVersion ID alone. {@link #verifyForNewContract(Reference)} must require the exact
 * publication to be active and must keep the Metric row lock until the caller's transaction ends.
 * {@link #inspect(Reference)} reports current evidence without mutating Metric state.</p>
 */
public interface MetricPublicationEvidenceProvider {

  Evidence inspect(Reference reference);

  Evidence verifyForNewContract(Reference reference);

  enum Status {
    VERIFIED_ACTIVE,
    WITHDRAWN,
    NOT_CURRENTLY_PUBLISHED,
    NOT_FOUND,
    REFERENCE_MISMATCH,
    VERSION_MISMATCH,
    DIGEST_MISMATCH,
    FORBIDDEN,
    UNAVAILABLE
  }

  /** Immutable identity copied into an ADS contract revision. */
  record Reference(
      Long metricId,
      Long metricVersionId,
      Integer metricVersion,
      String snapshotDigest,
      Long publicationEventId) {}

  /** Deliberately minimal: evidence responses never return a Metric snapshot or secret data. */
  record Evidence(Status status) {
    public Evidence {
      if (status == null) {
        status = Status.UNAVAILABLE;
      }
    }
  }
}
