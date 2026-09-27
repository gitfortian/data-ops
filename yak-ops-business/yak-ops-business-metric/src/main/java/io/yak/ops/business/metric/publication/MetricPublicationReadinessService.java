package io.yak.ops.business.metric.publication;

import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateStatus;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Fail-closed publication readiness coordinator.
 *
 * <p>This service only decides whether an immutable MetricVersion is ready for a future publication
 * command. It does not mutate Metric status and does not persist publication state/event yet.
 * READY and an explicit NOT_APPLICABLE are the only non-blocking gate outcomes; BLOCKED,
 * UNAVAILABLE and FORBIDDEN all fail closed.
 */
@Service
public class MetricPublicationReadinessService {

  public static final String GATE_PROVIDER_UNAVAILABLE = "publication-gates";

  private final MetricVersionRepository versionRepository;
  private final ObjectProvider<MetricPublicationGate> gates;

  public MetricPublicationReadinessService(
      MetricVersionRepository versionRepository,
      ObjectProvider<MetricPublicationGate> gates) {
    this.versionRepository = versionRepository;
    this.gates = gates;
  }

  public enum ReadinessStatus {
    READY,
    BLOCKED
  }

  public record PublicationReadiness(
      ReadinessStatus status,
      PublicationSubject subject,
      List<GateEvidence> gates) {
  }

  public PublicationReadiness check(Long metricId, int version) {
    MetricVersionPO metricVersion = requireVersion(metricId, version);
    PublicationSubject subject = new PublicationSubject(
        metricId,
        metricVersion.getId(),
        version,
        sha256(metricVersion.getSnapshot()));

    List<MetricPublicationGate> providers = gates.orderedStream().toList();
    if (providers.isEmpty()) {
      return new PublicationReadiness(
          ReadinessStatus.BLOCKED,
          subject,
          List.of(GateEvidence.unavailable(
              GATE_PROVIDER_UNAVAILABLE,
              "No publication gate provider is registered")));
    }

    List<GateEvidence> evidence = new ArrayList<>();
    for (MetricPublicationGate gate : providers) {
      evidence.add(evaluateSafely(gate, subject));
    }
    ReadinessStatus status = evidence.stream().allMatch(MetricPublicationReadinessService::satisfied)
        ? ReadinessStatus.READY
        : ReadinessStatus.BLOCKED;
    return new PublicationReadiness(status, subject, List.copyOf(evidence));
  }

  private static boolean satisfied(GateEvidence evidence) {
    return evidence.status() == GateStatus.READY
        || evidence.status() == GateStatus.NOT_APPLICABLE;
  }

  private static GateEvidence evaluateSafely(
      MetricPublicationGate gate, PublicationSubject subject) {
    String provider = gate.provider();
    try {
      GateEvidence evidence = gate.evaluate(subject);
      if (evidence == null) {
        return GateEvidence.unavailable(provider, "Gate returned no evidence");
      }
      String resolvedProvider = evidence.provider() == null || evidence.provider().isBlank()
          ? provider
          : evidence.provider();
      return new GateEvidence(
          resolvedProvider,
          evidence.status() == null ? GateStatus.UNAVAILABLE : evidence.status(),
          evidence.evidenceRef(),
          evidence.issues());
    } catch (RuntimeException exception) {
      return GateEvidence.unavailable(provider, exception.getMessage());
    }
  }

  private MetricVersionPO requireVersion(Long metricId, int version) {
    if (metricId == null || metricId <= 0 || version <= 0) {
      throw new MetricException(MetricErrorCode.NOT_FOUND, "指标版本不存在");
    }
    MetricVersionPO subject = versionRepository.findByMetricAndVersion(metricId, version);
    if (subject == null) {
      throw new MetricException(MetricErrorCode.NOT_FOUND,
          "指标 " + metricId + " 的版本 v" + version + " 不存在");
    }
    return subject;
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(
          digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
