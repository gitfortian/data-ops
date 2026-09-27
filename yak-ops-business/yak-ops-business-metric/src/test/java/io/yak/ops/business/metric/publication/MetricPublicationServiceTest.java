package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.exception.MetricException;
import io.yak.ops.business.metric.publication.MetricPublicationGate.GateEvidence;
import io.yak.ops.business.metric.publication.MetricPublicationGate.PublicationSubject;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.common.bean.po.metric.MetricActivePublicationPO;
import io.yak.ops.common.bean.po.metric.MetricPublicationEventPO;
import io.yak.ops.common.bean.po.metric.MetricVersionPO;
import io.yak.ops.common.enums.metric.MetricErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetricPublicationServiceTest {

  private static final String SNAPSHOT = "{\"metricCode\":\"gmv\"}";
  private static final String SNAPSHOT_DIGEST =
      "ed18bd58d1f3f17c3899425aa20a02c50359dc5da7414086bd975da165467f3a";

  private MetricPublicationRepository publicationRepository;
  private MetricVersionRepository versionRepository;
  private MetricPublicationReadinessService readinessService;
  private MetricPublicationService service;

  @BeforeEach
  void setUp() {
    publicationRepository = mock(MetricPublicationRepository.class);
    versionRepository = mock(MetricVersionRepository.class);
    readinessService = mock(MetricPublicationReadinessService.class);
    service = new MetricPublicationService(publicationRepository, versionRepository, readinessService);
  }

  @Test
  void rejectsStaleVersionBeforePublicationReadinessCanRaceWithDraftEdit() {
    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(4);

    assertThatThrownBy(() -> service.publish(7L, 3, "alice"))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> assertThat(((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.PUBLICATION_CONFLICT));

    verify(readinessService, never()).check(any(), anyInt());
    verify(publicationRepository, never()).appendEvent(any());
  }

  @Test
  void refusesPublishWhenAnyRequiredGateFailsClosed() {
    MetricVersionPO version = version(91L, 7L, 4, SNAPSHOT);
    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(4);
    when(versionRepository.findByMetricAndVersion(7L, 4)).thenReturn(version);
    when(publicationRepository.findActiveForUpdate(7L)).thenReturn(null);
    when(readinessService.check(7L, 4)).thenReturn(readiness(
        MetricPublicationReadinessService.ReadinessStatus.BLOCKED,
        GateEvidence.unavailable("semantic-provider", "provider unavailable")));

    assertThatThrownBy(() -> service.publish(7L, 4, "alice"))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> assertThat(((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.PUBLICATION_NOT_READY));

    verify(publicationRepository, never()).appendEvent(any());
    verify(publicationRepository, never()).replaceActive(any());
  }

  @Test
  void publishesExactImmutableVersionAndFreezesGateEvidence() {
    MetricVersionPO version = version(91L, 7L, 4, SNAPSHOT);
    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(4);
    when(versionRepository.findByMetricAndVersion(7L, 4)).thenReturn(version);
    when(publicationRepository.findActiveForUpdate(7L)).thenReturn(null);
    when(readinessService.check(7L, 4)).thenReturn(readiness(
        MetricPublicationReadinessService.ReadinessStatus.READY,
        GateEvidence.ready("definition", "VALIDATION:301"),
        GateEvidence.notApplicable("execution", "no Metric runtime")));
    when(publicationRepository.appendEvent(any())).thenAnswer(invocation -> {
      MetricPublicationEventPO event = invocation.getArgument(0);
      event.setId(501L);
      return event;
    });

    MetricPublicationService.PublishedMetricContract contract = service.publish(7L, 4, "alice");

    assertThat(contract.publicationEventId()).isEqualTo(501L);
    assertThat(contract.metricVersionId()).isEqualTo(91L);
    assertThat(contract.metricVersion()).isEqualTo(4);
    assertThat(contract.snapshotDigest()).isEqualTo(SNAPSHOT_DIGEST);
    assertThat(contract.snapshot()).isEqualTo(SNAPSHOT);
    assertThat(contract.publicationEvidence()).extracting(GateEvidence::provider)
        .containsExactly("definition", "execution");

    ArgumentCaptor<MetricPublicationEventPO> eventCaptor =
        ArgumentCaptor.forClass(MetricPublicationEventPO.class);
    verify(publicationRepository).appendEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getEventType()).isEqualTo("PUBLISHED");
    assertThat(eventCaptor.getValue().getSnapshotDigest()).isEqualTo(SNAPSHOT_DIGEST);
    assertThat(eventCaptor.getValue().getReadinessJson()).contains("VALIDATION:301");

    ArgumentCaptor<MetricActivePublicationPO> pointerCaptor =
        ArgumentCaptor.forClass(MetricActivePublicationPO.class);
    verify(publicationRepository).replaceActive(pointerCaptor.capture());
    assertThat(pointerCaptor.getValue().getPublicationEventId()).isEqualTo(501L);
    assertThat(pointerCaptor.getValue().getMetricVersionId()).isEqualTo(91L);
  }

  @Test
  void repeatedPublishOfSameActiveImmutableVersionIsIdempotentWithoutRecheckingTodaysGates() {
    MetricVersionPO version = version(91L, 7L, 4, SNAPSHOT);
    MetricActivePublicationPO active = active(501L, 91L, 7L, 4, SNAPSHOT_DIGEST);
    MetricPublicationEventPO event = publishedEvent(501L, 91L, 7L, 4, SNAPSHOT_DIGEST);
    event.setReadinessJson("[]");

    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(4);
    when(versionRepository.findByMetricAndVersion(7L, 4)).thenReturn(version);
    when(publicationRepository.findActiveForUpdate(7L)).thenReturn(active);
    when(publicationRepository.findEvent(501L)).thenReturn(event);

    MetricPublicationService.PublishedMetricContract contract = service.publish(7L, 4, "alice");

    assertThat(contract.publicationEventId()).isEqualTo(501L);
    verify(readinessService, never()).check(any(), anyInt());
    verify(publicationRepository, never()).appendEvent(any());
    verify(publicationRepository, never()).replaceActive(any());
  }

  @Test
  void withdrawAppendsLifecycleEventThenClearsOnlyExpectedPointer() {
    MetricActivePublicationPO active = active(501L, 91L, 7L, 4, SNAPSHOT_DIGEST);
    MetricPublicationEventPO published = publishedEvent(501L, 91L, 7L, 4, SNAPSHOT_DIGEST);
    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(5);
    when(publicationRepository.findActiveForUpdate(7L)).thenReturn(active);
    when(publicationRepository.findEvent(501L)).thenReturn(published);
    when(publicationRepository.appendEvent(any())).thenAnswer(invocation -> {
      MetricPublicationEventPO event = invocation.getArgument(0);
      event.setId(502L);
      return event;
    });
    when(publicationRepository.clearActive(7L, 501L)).thenReturn(true);

    MetricPublicationService.WithdrawalResult result = service.withdraw(7L, "bob");

    assertThat(result.withdrawn()).isTrue();
    assertThat(result.event().eventType()).isEqualTo("WITHDRAWN");
    assertThat(result.event().subjectPublicationId()).isEqualTo(501L);
    verify(publicationRepository).clearActive(7L, 501L);
  }

  @Test
  void withdrawWithoutActivePublicationIsIdempotent() {
    when(publicationRepository.lockCurrentMetricVersion(7L)).thenReturn(5);
    when(publicationRepository.findActiveForUpdate(7L)).thenReturn(null);

    MetricPublicationService.WithdrawalResult result = service.withdraw(7L, "bob");

    assertThat(result.withdrawn()).isFalse();
    verify(publicationRepository, never()).appendEvent(any());
  }

  @Test
  void refusesToInventPublicationActorWhenAuthenticationIdentityIsMissing() {
    assertThatThrownBy(() -> service.publish(7L, 4, " "))
        .isInstanceOf(MetricException.class)
        .satisfies(error -> assertThat(((MetricException) error).getErrorCode())
            .isEqualTo(MetricErrorCode.PUBLICATION_CONFLICT));

    verify(publicationRepository, never()).lockCurrentMetricVersion(any());
  }

  private static MetricPublicationReadinessService.PublicationReadiness readiness(
      MetricPublicationReadinessService.ReadinessStatus status,
      GateEvidence... gates) {
    return new MetricPublicationReadinessService.PublicationReadiness(
        status,
        new PublicationSubject(7L, 91L, 4, SNAPSHOT_DIGEST),
        List.of(gates));
  }

  private static MetricVersionPO version(
      Long id, Long metricId, int version, String snapshot) {
    MetricVersionPO po = new MetricVersionPO();
    po.setId(id);
    po.setMetricId(metricId);
    po.setVersion(version);
    po.setSnapshot(snapshot);
    return po;
  }

  private static MetricActivePublicationPO active(
      Long eventId, Long versionId, Long metricId, int version, String digest) {
    MetricActivePublicationPO po = new MetricActivePublicationPO();
    po.setMetricId(metricId);
    po.setPublicationEventId(eventId);
    po.setMetricVersionId(versionId);
    po.setMetricVersion(version);
    po.setSnapshotDigest(digest);
    po.setPublishedBy("alice");
    po.setPublishedAt(LocalDateTime.now());
    return po;
  }

  private static MetricPublicationEventPO publishedEvent(
      Long id, Long versionId, Long metricId, int version, String digest) {
    MetricPublicationEventPO po = new MetricPublicationEventPO();
    po.setId(id);
    po.setMetricId(metricId);
    po.setMetricVersionId(versionId);
    po.setMetricVersion(version);
    po.setSnapshotDigest(digest);
    po.setEventType("PUBLISHED");
    po.setActedBy("alice");
    po.setActedAt(LocalDateTime.now());
    return po;
  }
}
