package io.yak.ops.business.metric.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.metric.api.MetricExplanationQueryApi;
import io.yak.ops.business.metric.dao.model.MetricActivePublicationPO;
import io.yak.ops.business.metric.dao.model.MetricPublicationEventPO;
import io.yak.ops.business.metric.domain.MetricUsage;
import io.yak.ops.business.metric.domain.MetricValidationEvidence;
import io.yak.ops.business.metric.impact.MetricObservedUsageProvider;
import io.yak.ops.business.metric.repository.MetricPublicationRepository;
import io.yak.ops.business.metric.repository.MetricUsageRepository;
import io.yak.ops.business.metric.repository.MetricValidationEvidenceRepository;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;

class MetricChangeReviewQueryAdapterTest {
  private final ActionAuthorization authorization = mock(ActionAuthorization.class);
  private final MetricExplanationQueryApi snapshots = mock(MetricExplanationQueryApi.class);
  private final MetricPublicationRepository publications = mock(MetricPublicationRepository.class);
  private final MetricValidationEvidenceRepository validations = mock(MetricValidationEvidenceRepository.class);
  private final MetricUsageRepository usages = mock(MetricUsageRepository.class);
  @SuppressWarnings("unchecked") private final ObjectProvider<MetricObservedUsageProvider> observed = mock(ObjectProvider.class);
  private final MetricChangeReviewQueryAdapter query = new MetricChangeReviewQueryAdapter(authorization, snapshots, publications, validations, usages, observed);
  private final LocalDateTime checkedAt = LocalDateTime.of(2026, 10, 8, 10, 0);
  private MetricActivePublicationPO active;
  private MetricExplanationQueryApi.Context snapshot(int version, String value) {
    return new MetricExplanationQueryApi.Context(version == 3 ? 19 : 11, 7, version, (version == 3 ? "a" : "b").repeat(64),
        List.of(new MetricExplanationQueryApi.Fact("measureExpr", "度量", value)));
  }
  @BeforeEach void source() {
    when(snapshots.require(7, 3)).thenReturn(snapshot(3, "SUM(amount)"));
    when(snapshots.requireSnapshot(7, 2)).thenReturn(snapshot(2, "COUNT(amount)"));
    active = new MetricActivePublicationPO(); active.setMetricId(7L); active.setPublicationEventId(31L);
    active.setMetricVersionId(11L); active.setMetricVersion(2); active.setSnapshotDigest("b".repeat(64));
    when(publications.findActive(7L)).thenReturn(active);
    var event = new MetricPublicationEventPO(); event.setMetricId(7L); event.setEventType("PUBLISHED");
    event.setMetricVersionId(11L); event.setMetricVersion(2); event.setSnapshotDigest("b".repeat(64));
    when(publications.findEvent(31L)).thenReturn(event);
    when(usages.listByMetricBounded(7L, 21)).thenReturn(List.of()); when(observed.stream()).thenAnswer(i -> Stream.empty());
  }
  @Test void permissionFailurePrecedesEverySourceRead() {
    doThrow(new SecurityException("revoked")).when(authorization).requirePermission(MetricPermissionCode.READ);
    assertThrows(SecurityException.class, () -> query.prepare(7, 3));
    verifyNoInteractions(snapshots, publications, validations, usages, observed);
  }
  @Test void exactVersionPairDiffAndStableFingerprintDoNotUseReadTime() {
    var first = query.prepare(7, 3); var second = query.prepare(7, 3);
    assertEquals("READY", first.status()); assertEquals(2, first.publishedVersion()); assertEquals(31L, first.publicationEventId());
    assertEquals("COUNT(amount)", first.differences().getFirst().before()); assertEquals("SUM(amount)", first.differences().getFirst().after());
    assertEquals(first.definition(), second.definition()); assertNotNull(first.preparedAt());
    assertEquals("EMPTY", first.coverage().getFirst().status());
    assertEquals("NOT_APPLICABLE", first.coverage().getLast().status());
    verify(usages, never()).listByMetric(anyLong()); verify(validations, never()).listByVersion(anyLong(), anyInt());
  }
  @ParameterizedTest @ValueSource(strings = {"ATOMIC", "DERIVED", "COMPOSITE"})
  void allMetricTypesKeepExactSnapshotValues(String type) {
    when(snapshots.require(7, 3)).thenReturn(new MetricExplanationQueryApi.Context(19, 7, 3, "a".repeat(64), List.of(
        new MetricExplanationQueryApi.Fact("metricType", "类型", type),
        new MetricExplanationQueryApi.Fact("compositions", "组成", "[{\"subMetricVersion\":4}]"))));
    var value = query.prepare(7, 3);
    assertTrue(value.facts().stream().anyMatch(f -> f.value().equals(type)));
    assertTrue(value.facts().stream().anyMatch(f -> f.value().contains("subMetricVersion")));
  }
  @Test void noPublicationDoesNotGuessBaselineAndUnchangedFactsDoNotRequireInference() {
    when(publications.findActive(7L)).thenReturn(null);
    assertEquals("NO_BASELINE", query.prepare(7, 3).status()); verifyNoInteractions(validations, usages, observed);
    when(publications.findActive(7L)).thenReturn(active); when(snapshots.requireSnapshot(7, 2)).thenReturn(snapshot(2, "SUM(amount)"));
    assertEquals("UNCHANGED", query.prepare(7, 3).status());
  }
  @Test void latestFailedAttemptIsNotReplacedByAnOldPassedAttempt() {
    when(validations.findLatest(7L, 3)).thenReturn(validation(MetricValidationEvidence.ProviderState.READY, "a".repeat(64)));
    var value = query.prepare(7, 3);
    assertTrue(value.facts().stream().anyMatch(f -> f.key().equals("validation.result") && f.value().equals("FAILED")));
    assertTrue(value.facts().stream().anyMatch(f -> f.key().equals("validation.identity") && f.value().contains("evidence:40")));
    verify(validations, never()).findLatestReady(anyLong(), anyInt());
  }
  @Test void unavailableOrMismatchedValidationNeverLeaksIssuesOrBecomesPassed() {
    when(validations.findLatest(7L, 3)).thenReturn(validation(MetricValidationEvidence.ProviderState.FORBIDDEN, "a".repeat(64)));
    var denied = query.prepare(7, 3); assertEquals("FORBIDDEN", denied.coverage().getFirst().status());
    assertFalse(denied.facts().toString().contains("private-issue"));
    when(validations.findLatest(7L, 3)).thenReturn(validation(MetricValidationEvidence.ProviderState.READY, "wrong"));
    assertEquals("UNAVAILABLE", query.prepare(7, 3).coverage().getFirst().status());
  }
  @Test void referenceSlicePreservesUnknownVersionsAndSignalsOmittedEvidence() {
    when(usages.listByMetricBounded(7L, 21)).thenReturn(IntStream.rangeClosed(1, 21).mapToObj(i ->
        new MetricUsage((long)i, 7L, null, "DATASET", (long)i, "private-name", checkedAt)).toList());
    var value = query.prepare(7, 3);
    assertEquals(20, value.facts().stream().filter(f -> f.key().startsWith("reference.")).count());
    assertTrue(value.facts().stream().anyMatch(f -> f.value().contains("version:未知")));
    assertTrue(value.coverage().get(1).description().contains("仍有未展示")); assertFalse(value.toString().contains("private-name"));
  }
  @Test void providerFailureIsNotZeroReferencesAndRawExceptionNeverBecomesFact() {
    when(usages.listByMetricBounded(7L, 21)).thenThrow(new IllegalStateException("private failure jdbc://secret"));
    var value = query.prepare(7, 3); assertEquals("UNAVAILABLE", value.coverage().get(1).status());
    assertFalse(value.toString().contains("jdbc"));
  }
  @Test void evidenceChangeChangesDigestAndPublicationRaceRejects() {
    String before = query.prepare(7, 3).definition();
    when(validations.findLatest(7L, 3)).thenReturn(validation(MetricValidationEvidence.ProviderState.READY, "a".repeat(64)));
    assertNotEquals(before, query.prepare(7, 3).definition());
    when(publications.findActive(7L)).thenReturn(active, null);
    assertThrows(IllegalStateException.class, () -> query.prepare(7, 3));
  }
  @Test void ledgerMismatchAndTotalContextOverflowFailClosed() {
    when(publications.findEvent(31L)).thenReturn(null); assertThrows(IllegalStateException.class, () -> query.prepare(7, 3));
    source();
    when(snapshots.require(7, 3)).thenReturn(new MetricExplanationQueryApi.Context(19, 7, 3, "a".repeat(64),
        IntStream.range(0, 10).mapToObj(i -> new MetricExplanationQueryApi.Fact("f"+i, "字段", "x".repeat(4096))).toList()));
    assertThrows(IllegalStateException.class, () -> query.prepare(7, 3));
  }
  private MetricValidationEvidence validation(MetricValidationEvidence.ProviderState state, String digest) {
    return new MetricValidationEvidence(40L, 7L, 19L, 3, MetricValidationEvidence.ValidationResult.FAILED, state,
        List.of(new MetricValidationEvidence.ValidationIssue("REQUIRED", "modelId", "private-issue", MetricValidationEvidence.Severity.BLOCKER)),
        "metric-definition", digest, "user", checkedAt);
  }
}
