package io.yak.ops.business.metric.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.metric.dao.model.MetricActivePublicationPO;
import io.yak.ops.business.metric.dao.model.MetricPublicationEventPO;
import io.yak.ops.business.metric.dao.model.MetricVersionPO;
import io.yak.ops.business.metric.domain.Metric;
import io.yak.ops.business.metric.domain.MetricStatus;
import io.yak.ops.business.metric.domain.MetricType;
import io.yak.ops.business.metric.domain.StatPeriod;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricRepository;
import io.yak.ops.business.metric.repository.MetricVersionRepository;
import io.yak.ops.business.metric.support.MetricSnapshotDigest;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Evidence;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Reference;
import io.yak.ops.spi.metric.MetricPublicationEvidenceProvider.Status;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricPublicationEvidenceProviderImplTest {

  private static final Long METRIC_ID = 7L;
  private static final Long VERSION_ID = 91L;
  private static final int VERSION_NO = 4;
  private static final Long EVENT_ID = 501L;
  private static final String SNAPSHOT = "{\"metricCode\":\"gmv\"}";
  private static final String DIGEST = MetricSnapshotDigest.sha256(SNAPSHOT);
  private static final Reference PIN =
      new Reference(METRIC_ID, VERSION_ID, VERSION_NO, DIGEST, EVENT_ID);

  private final CurrentProject currentProject = mock(CurrentProject.class);
  private final MetricPublicationReadAccess readAccess = mock(MetricPublicationReadAccess.class);
  private final MetricRepository metrics = mock(MetricRepository.class);
  private final MetricVersionRepository versions = mock(MetricVersionRepository.class);
  private final MetricPublicationRepository publications = mock(MetricPublicationRepository.class);
  private final MetricPublicationEvidenceProviderImpl provider =
      new MetricPublicationEvidenceProviderImpl(
          currentProject, readAccess, metrics, versions, publications);

  @BeforeEach
  void setUp() {
    when(currentProject.requireProjectId()).thenReturn(12L);
    when(readAccess.canRead(12L)).thenReturn(true);
    when(metrics.findById(METRIC_ID)).thenReturn(Optional.of(metric()));
    when(versions.findByMetricAndVersion(METRIC_ID, VERSION_NO))
        .thenReturn(version());
    when(publications.findEvent(EVENT_ID)).thenReturn(publishedEvent());
    when(publications.findActive(METRIC_ID)).thenReturn(activePublication());
  }

  @Test
  void verifiesExactActivePinAndLocksMetricForNewContract() {
    when(publications.lockCurrentMetricVersion(METRIC_ID)).thenReturn(5);

    Evidence evidence = provider.verifyForNewContract(PIN);

    assertThat(evidence.status()).isEqualTo(Status.VERIFIED_ACTIVE);
    verify(publications).lockCurrentMetricVersion(METRIC_ID);
  }

  @Test
  void reportsVersionMismatchWhenThePublicationEventPinsAnotherVersion() {
    Reference alteredVersion =
        new Reference(METRIC_ID, 999L, VERSION_NO, DIGEST, EVENT_ID);

    Evidence evidence = provider.inspect(alteredVersion);

    assertThat(evidence.status()).isEqualTo(Status.VERSION_MISMATCH);
    verify(versions, never()).findByMetricAndVersion(METRIC_ID, VERSION_NO);
  }

  @Test
  void reportsDigestMismatchInsteadOfTreatingTheReferenceAsMissing() {
    Reference alteredDigest =
        new Reference(METRIC_ID, VERSION_ID, VERSION_NO, "c".repeat(64), EVENT_ID);

    Evidence evidence = provider.inspect(alteredDigest);

    assertThat(evidence.status()).isEqualTo(Status.DIGEST_MISMATCH);
  }

  @Test
  void refusesCrossProjectOrMissingMetricWithoutLookingUpPublicationLedger() {
    when(metrics.findById(METRIC_ID)).thenReturn(Optional.empty());

    Evidence evidence = provider.inspect(PIN);

    assertThat(evidence.status()).isEqualTo(Status.NOT_FOUND);
    verify(publications, never()).findEvent(EVENT_ID);
  }

  @Test
  void refusesReadBeforeQueryingMetricWhenCurrentUserLacksMetricRead() {
    when(readAccess.canRead(12L)).thenReturn(false);

    Evidence evidence = provider.inspect(PIN);

    assertThat(evidence.status()).isEqualTo(Status.FORBIDDEN);
    verify(metrics, never()).findById(METRIC_ID);
    verify(publications, never()).findEvent(EVENT_ID);
  }

  @Test
  void reportsExplicitWithdrawalWithoutLeakingOtherProjectRecords() {
    when(publications.findActive(METRIC_ID)).thenReturn(null);
    MetricPublicationEventPO withdrawal = new MetricPublicationEventPO();
    withdrawal.setId(502L);
    withdrawal.setMetricId(METRIC_ID);
    withdrawal.setMetricVersionId(VERSION_ID);
    withdrawal.setMetricVersion(VERSION_NO);
    withdrawal.setSnapshotDigest(DIGEST);
    withdrawal.setEventType("WITHDRAWN");
    withdrawal.setSubjectPublicationId(EVENT_ID);
    when(publications.listEvents(METRIC_ID)).thenReturn(List.of(publishedEvent(), withdrawal));

    Evidence evidence = provider.inspect(PIN);

    assertThat(evidence.status()).isEqualTo(Status.WITHDRAWN);
  }

  @Test
  void reportsSupersededPublicationAsNotCurrentRatherThanAsNotFound() {
    MetricActivePublicationPO newer = activePublication();
    newer.setPublicationEventId(999L);
    newer.setMetricVersionId(92L);
    newer.setMetricVersion(5);
    newer.setSnapshotDigest("d".repeat(64));
    when(publications.findActive(METRIC_ID)).thenReturn(newer);
    when(publications.listEvents(METRIC_ID)).thenReturn(List.of(publishedEvent()));

    Evidence evidence = provider.inspect(PIN);

    assertThat(evidence.status()).isEqualTo(Status.NOT_CURRENTLY_PUBLISHED);
  }

  @Test
  void convertsProviderFailuresToUnavailableAndNeverToVerified() {
    when(metrics.findById(METRIC_ID)).thenThrow(new IllegalStateException("database unavailable"));

    Evidence evidence = provider.inspect(PIN);

    assertThat(evidence.status()).isEqualTo(Status.UNAVAILABLE);
  }

  private static Metric metric() {
    LocalDateTime now = LocalDateTime.now();
    return new Metric(METRIC_ID, "gmv", "GMV", null, null, MetricType.ATOMIC,
        null, null, null, null, null, null, null, null, null, null,
        StatPeriod.DAY, null, null, null, MetricStatus.ENABLED, 5,
        "alice", "alice", now, now);
  }

  private static MetricVersionPO version() {
    MetricVersionPO po = new MetricVersionPO();
    po.setId(VERSION_ID);
    po.setMetricId(METRIC_ID);
    po.setVersion(VERSION_NO);
    po.setSnapshot(SNAPSHOT);
    return po;
  }

  private static MetricPublicationEventPO publishedEvent() {
    MetricPublicationEventPO event = new MetricPublicationEventPO();
    event.setId(EVENT_ID);
    event.setMetricId(METRIC_ID);
    event.setMetricVersionId(VERSION_ID);
    event.setMetricVersion(VERSION_NO);
    event.setSnapshotDigest(DIGEST);
    event.setEventType("PUBLISHED");
    return event;
  }

  private static MetricActivePublicationPO activePublication() {
    MetricActivePublicationPO active = new MetricActivePublicationPO();
    active.setMetricId(METRIC_ID);
    active.setPublicationEventId(EVENT_ID);
    active.setMetricVersionId(VERSION_ID);
    active.setMetricVersion(VERSION_NO);
    active.setSnapshotDigest(DIGEST);
    return active;
  }
}
