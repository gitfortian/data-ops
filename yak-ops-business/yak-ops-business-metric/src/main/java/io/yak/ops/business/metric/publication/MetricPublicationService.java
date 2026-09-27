package io.yak.ops.business.metric.publication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.common.bean.po.metric.MetricActivePublicationPO;
import io.yak.ops.common.bean.po.metric.MetricPublicationEventPO;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the explicit Metric publication lifecycle.
 *
 * <p>Publication never mutates the editable Metric definition or MetricVersion. A PUBLISHED event
 * freezes the exact immutable version identity, digest and publication-time gate evidence; a small
 * active pointer selects which published contract is currently effective. Withdrawal appends a
 * lifecycle event and clears only that pointer, leaving the publication ledger intact.
 */
@Service
public class MetricPublicationService {

  static final String EVENT_PUBLISHED = "PUBLISHED";
  static final String EVENT_WITHDRAWN = "WITHDRAWN";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
  private static final TypeReference<List<GateEvidence>> GATE_LIST = new TypeReference<>() {};

  private final MetricPublicationRepository publicationRepository;
  private final MetricVersionRepository versionRepository;
  private final MetricPublicationReadinessService readinessService;

  public MetricPublicationService(
      MetricPublicationRepository publicationRepository,
      MetricVersionRepository versionRepository,
      MetricPublicationReadinessService readinessService) {
    this.publicationRepository = publicationRepository;
    this.versionRepository = versionRepository;
    this.readinessService = readinessService;
  }

  public record PublishedMetricContract(
      Long publicationEventId,
      Long metricId,
      Long metricVersionId,
      int metricVersion,
      String snapshotDigest,
      String snapshot,
      List<GateEvidence> publicationEvidence,
      String publishedBy,
      LocalDateTime publishedAt) {
  }

  public record PublicationEventView(
      Long id,
      String eventType,
      Long subjectPublicationId,
      Long metricId,
      Long metricVersionId,
      int metricVersion,
      String snapshotDigest,
      List<GateEvidence> publicationEvidence,
      String actedBy,
      LocalDateTime actedAt) {
  }

  public record WithdrawalResult(boolean withdrawn, PublicationEventView event) {
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public PublishedMetricContract publish(Long metricId, int version, String operator) {
    Integer currentVersion = publicationRepository.lockCurrentMetricVersion(metricId);
    if (currentVersion == null) {
      throw new MetricException(MetricErrorCode.NOT_FOUND, String.valueOf(metricId));
    }
    if (version <= 0 || currentVersion != version) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_CONFLICT,
          "只能发布当前 immutable version；请求 v" + version + "，当前 v" + currentVersion);
    }

    MetricPublicationReadinessService.PublicationReadiness readiness = readinessService.check(metricId, version);
    if (readiness.status() != MetricPublicationReadinessService.ReadinessStatus.READY) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_NOT_READY,
          blockingSummary(readiness.gates()));
    }

    MetricVersionPO metricVersion = versionRepository.findByMetricAndVersion(metricId, version);
    if (metricVersion == null) {
      throw new MetricException(MetricErrorCode.NOT_FOUND,
          "指标 " + metricId + " 的版本 v" + version + " 不存在");
    }
    requireSameSubject(metricVersion, readiness.subject());

    MetricActivePublicationPO active = publicationRepository.findActiveForUpdate(metricId);
    if (active != null
        && Objects.equals(active.getMetricVersionId(), metricVersion.getId())
        && Objects.equals(active.getSnapshotDigest(), readiness.subject().snapshotDigest())) {
      return requireActiveContract(active);
    }

    LocalDateTime now = LocalDateTime.now();
    MetricPublicationEventPO event = new MetricPublicationEventPO();
    event.setMetricId(metricId);
    event.setMetricVersionId(metricVersion.getId());
    event.setMetricVersion(version);
    event.setSnapshotDigest(readiness.subject().snapshotDigest());
    event.setEventType(EVENT_PUBLISHED);
    event.setReadinessJson(writeEvidence(readiness.gates()));
    event.setActedBy(normalizeOperator(operator));
    event.setActedAt(now);
    publicationRepository.appendEvent(event);

    MetricActivePublicationPO pointer = new MetricActivePublicationPO();
    pointer.setMetricId(metricId);
    pointer.setPublicationEventId(event.getId());
    pointer.setMetricVersionId(metricVersion.getId());
    pointer.setMetricVersion(version);
    pointer.setSnapshotDigest(readiness.subject().snapshotDigest());
    pointer.setPublishedBy(event.getActedBy());
    pointer.setPublishedAt(now);
    publicationRepository.replaceActive(pointer);

    return contract(event, metricVersion, readiness.gates());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public WithdrawalResult withdraw(Long metricId, String operator) {
    Integer currentVersion = publicationRepository.lockCurrentMetricVersion(metricId);
    if (currentVersion == null) {
      throw new MetricException(MetricErrorCode.NOT_FOUND, String.valueOf(metricId));
    }

    MetricActivePublicationPO active = publicationRepository.findActiveForUpdate(metricId);
    if (active == null) {
      return new WithdrawalResult(false, null);
    }

    MetricPublicationEventPO published = publicationRepository.findEvent(active.getPublicationEventId());
    if (published == null || !EVENT_PUBLISHED.equals(published.getEventType())) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_CONFLICT,
          "当前发布指针缺少对应的 PUBLISHED ledger event");
    }

    MetricPublicationEventPO withdrawn = new MetricPublicationEventPO();
    withdrawn.setMetricId(metricId);
    withdrawn.setMetricVersionId(active.getMetricVersionId());
    withdrawn.setMetricVersion(active.getMetricVersion());
    withdrawn.setSnapshotDigest(active.getSnapshotDigest());
    withdrawn.setEventType(EVENT_WITHDRAWN);
    withdrawn.setSubjectPublicationId(active.getPublicationEventId());
    withdrawn.setActedBy(normalizeOperator(operator));
    withdrawn.setActedAt(LocalDateTime.now());
    publicationRepository.appendEvent(withdrawn);

    if (!publicationRepository.clearActive(metricId, active.getPublicationEventId())) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_CONFLICT,
          "发布状态已变化，请刷新后重试撤回");
    }
    return new WithdrawalResult(true, eventView(withdrawn));
  }

  public PublishedMetricContract active(Long metricId) {
    MetricActivePublicationPO active = publicationRepository.findActive(metricId);
    return active == null ? null : requireActiveContract(active);
  }

  public List<PublicationEventView> history(Long metricId) {
    return publicationRepository.listEvents(metricId).stream()
        .map(this::eventView)
        .toList();
  }

  private PublishedMetricContract requireActiveContract(MetricActivePublicationPO active) {
    MetricPublicationEventPO event = publicationRepository.findEvent(active.getPublicationEventId());
    MetricVersionPO version = versionRepository.findByMetricAndVersion(
        active.getMetricId(), active.getMetricVersion());
    if (event == null || version == null
        || !Objects.equals(version.getId(), active.getMetricVersionId())
        || !Objects.equals(event.getMetricVersionId(), active.getMetricVersionId())
        || !Objects.equals(event.getSnapshotDigest(), active.getSnapshotDigest())) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_CONFLICT,
          "当前 Published Metric Contract 的 ledger/version identity 不完整");
    }
    return contract(event, version, readEvidence(event.getReadinessJson()));
  }

  private PublishedMetricContract contract(
      MetricPublicationEventPO event, MetricVersionPO version, List<GateEvidence> evidence) {
    return new PublishedMetricContract(
        event.getId(),
        event.getMetricId(),
        event.getMetricVersionId(),
        event.getMetricVersion(),
        event.getSnapshotDigest(),
        version.getSnapshot(),
        evidence == null ? List.of() : List.copyOf(evidence),
        event.getActedBy(),
        event.getActedAt());
  }

  private PublicationEventView eventView(MetricPublicationEventPO event) {
    return new PublicationEventView(
        event.getId(),
        event.getEventType(),
        event.getSubjectPublicationId(),
        event.getMetricId(),
        event.getMetricVersionId(),
        event.getMetricVersion(),
        event.getSnapshotDigest(),
        readEvidence(event.getReadinessJson()),
        event.getActedBy(),
        event.getActedAt());
  }

  private static void requireSameSubject(
      MetricVersionPO version, MetricPublicationGate.PublicationSubject subject) {
    if (!Objects.equals(version.getId(), subject.metricVersionId())
        || version.getVersion() == null
        || version.getVersion() != subject.metricVersion()) {
      throw new MetricException(
          MetricErrorCode.PUBLICATION_CONFLICT,
          "publication readiness 与 immutable MetricVersion 身份不一致");
    }
  }

  private static String blockingSummary(List<GateEvidence> gates) {
    String summary = gates == null ? "" : gates.stream()
        .filter(gate -> gate.status() != MetricPublicationGate.GateStatus.READY
            && gate.status() != MetricPublicationGate.GateStatus.NOT_APPLICABLE)
        .map(gate -> gate.provider() + "=" + gate.status()
            + (gate.issues().isEmpty() ? "" : "(" + String.join("; ", gate.issues()) + ")"))
        .collect(Collectors.joining(", "));
    return summary.isBlank() ? "publication readiness 未通过" : summary;
  }

  private static String normalizeOperator(String operator) {
    return operator == null || operator.isBlank() ? "system" : operator.trim();
  }

  private static String writeEvidence(List<GateEvidence> gates) {
    try {
      return OBJECT_MAPPER.writeValueAsString(gates == null ? List.of() : gates);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cannot serialize publication gate evidence", exception);
    }
  }

  private static List<GateEvidence> readEvidence(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return OBJECT_MAPPER.readValue(json, GATE_LIST);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Cannot deserialize publication gate evidence", exception);
    }
  }
}
