package io.yak.ops.business.metric.publication;

import java.util.List;

/**
 * Publication prerequisite contract.
 *
 * <p>Each gate evaluates an immutable MetricVersion subject and returns explicit
 * evidence. Publication orchestration must fail closed when a required provider is
 * unavailable; it must never reinterpret MetricStatus.ENABLED as publication approval.
 */
public interface MetricPublicationGate {

  String provider();

  GateEvidence evaluate(PublicationSubject subject);

  enum GateStatus {
    READY,
    BLOCKED,
    UNAVAILABLE
  }

  record PublicationSubject(
      Long metricId,
      Long metricVersionId,
      int metricVersion,
      String snapshotDigest) {
  }

  record GateEvidence(
      String provider,
      GateStatus status,
      String evidenceRef,
      List<String> issues) {

    public GateEvidence {
      issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static GateEvidence ready(String provider, String evidenceRef) {
      return new GateEvidence(provider, GateStatus.READY, evidenceRef, List.of());
    }

    public static GateEvidence blocked(String provider, String evidenceRef, List<String> issues) {
      return new GateEvidence(provider, GateStatus.BLOCKED, evidenceRef, issues);
    }

    public static GateEvidence unavailable(String provider, String reason) {
      return new GateEvidence(
          provider,
          GateStatus.UNAVAILABLE,
          null,
          reason == null || reason.isBlank() ? List.of() : List.of(reason));
    }
  }
}
