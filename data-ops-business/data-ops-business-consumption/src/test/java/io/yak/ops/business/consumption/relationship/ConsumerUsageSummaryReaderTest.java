package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAccessDeniedException;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConsumerUsageSummaryReaderTest {
  private final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
  private final UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
  private final CurrentProject project = mock(CurrentProject.class);
  private final ConsumerUsageSummaryReader reader = new ConsumerUsageSummaryReader(subscriptions, usage, project);
  private final ProductKey product = ProductKey.parse("DATASET:101");
  private final LocalDateTime at = LocalDateTime.of(2026, 10, 8, 10, 0);

  @BeforeEach void project() { when(project.requireProjectId()).thenReturn(42L); }

  private ConsumerRef consumer(String identity) {
    return new ConsumerRef(ConsumerType.DASHBOARD, "DASHBOARD", identity, "private display");
  }
  private Subscription subscription(long projectId, ProductKey key, String identity, SubscriptionStatus status) {
    return new Subscription(1L, projectId, key, consumer(identity), ConsumptionMode.QUERY, status,
        "private actor", at, "private actor", at);
  }
  private UsageEvidence observed(long projectId, ProductKey key, String identity) {
    return new UsageEvidence(2L, projectId, key, new SourceVersionRef("9001", "v1"), consumer(identity),
        at, ConsumptionMode.QUERY, UsageOutcome.SUCCESS, "DATASET_QUERY_PERFORMANCE", "private ref", "dedup", at);
  }

  @Test void providerReadsOnlyPersistedWindowsAndPreservesScopeWithoutIdentityOrRawEvidence() {
    when(subscriptions.listRecentActive(42L, product, 200)).thenReturn(List.of(
        subscription(42, product, "9", SubscriptionStatus.ACTIVE)));
    when(usage.list(42L, product, null, 200)).thenReturn(List.of(observed(42, product, "9"), observed(42, product, "10")));
    var provider = new ConsumptionAssetUsageSectionProvider(reader);
    var result = provider.query(new SectionContext("dataset:101", "DATASET", "101", Map.of("returnAssetId", "7")));
    assertEquals(SectionStatus.OK, result.status());
    assertEquals("CONSUMING_DOMAINS", result.ownerDomain());
    var facts = result.summary().values();
    assertEquals(2, facts.get("consumerCount")); // stable identity union, not subscriptions + events
    assertEquals(2, facts.get("dashboardCount"));
    assertEquals(2, facts.get("successfulUsageCount"));
    assertEquals(1, facts.get("activeSubscriptionCount"));
    assertEquals("READY", facts.get("subscriptionState"));
    assertEquals("READY", facts.get("usageState"));
    assertEquals("WITHIN_LIMIT", facts.get("usageWindowState"));
    assertEquals("NOT_PERFORMED", facts.get("sourceReconciliation"));
    assertFalse(facts.toString().contains("private"));
    assertTrue(result.actions().getFirst().target().contains("DATASET%3A101?returnAssetId=7"));
    assertNull(result.updatedAt());
    verify(subscriptions).listRecentActive(42L, product, 200);
    verify(usage).list(42L, product, null, 200);
    verifyNoMoreInteractions(subscriptions, usage); // no unbounded list, save or normalization lookup
  }

  @ParameterizedTest @CsvSource({"0,1", "1,1", "20,20", "201,200", "2147483647,200"})
  void clampsBothWindowsAndMarksSaturationWithoutClaimingTruncation(int requested, int limit) {
    when(subscriptions.listRecentActive(42L, product, limit)).thenReturn(
        java.util.stream.IntStream.range(0, limit).mapToObj(i -> subscription(42, product, "9", SubscriptionStatus.ACTIVE)).toList());
    when(usage.list(42L, product, null, limit)).thenReturn(
        java.util.stream.IntStream.range(0, limit).mapToObj(i -> observed(42, product, "9")).toList());
    var result = reader.read(product, requested);
    assertEquals(1, result.consumerCount());
    assertEquals(limit, result.successfulUsageCount());
    assertEquals(limit, result.activeSubscriptionCount());
    assertEquals(limit, result.subscriptionWindowLimit());
    assertEquals(limit, result.usageWindowLimit());
    assertEquals("LIMIT_REACHED", result.subscriptionWindowState());
    assertEquals("LIMIT_REACHED", result.usageWindowState());
    assertTrue(result.coverageNote().contains("may omit"));
  }

  @Test void emptySnapshotIsNotReconciledAndDoesNotInventAnObservationTime() {
    when(subscriptions.listRecentActive(42L, product, 200)).thenReturn(List.of());
    when(usage.list(42L, product, null, 200)).thenReturn(List.of());
    var result = new ConsumptionAssetUsageSectionProvider(reader).query(new SectionContext("dataset:101", "DATASET", "101"));
    assertEquals(SectionStatus.EMPTY, result.status());
    assertEquals("EMPTY", result.summary().values().get("usageState"));
    assertEquals(0, result.summary().values().get("successfulUsageCount"));
    assertNull(result.summary().values().get("lastObservedAt"));
    assertNull(result.provenance().observedAt());
    assertTrue(result.reason().contains("不证明没有消费者"));
    assertTrue(result.evidence().isEmpty());
  }

  @Test void unavailableAndForbiddenSidesStayUnknownWhileReadableEvidenceSurvives() {
    when(subscriptions.listRecentActive(42L, product, 200)).thenThrow(new ActionAccessDeniedException("private"));
    when(usage.list(42L, product, null, 200)).thenReturn(List.of(observed(42, product, "9")));
    var result = reader.read(product, 200);
    assertEquals(SectionStatus.OK, result.status());
    assertEquals("FORBIDDEN", result.subscriptionState().name());
    assertNull(result.activeSubscriptionCount());
    assertEquals("UNKNOWN", result.subscriptionWindowState());
    assertEquals(1, result.successfulUsageCount());
    doReturn(List.of(subscription(42, product, "9", SubscriptionStatus.ACTIVE)))
        .when(subscriptions).listRecentActive(42L, product, 200);
    when(usage.list(42L, product, null, 200)).thenThrow(new IllegalStateException("private jdbc:secret"));
    result = reader.read(product, 200);
    assertEquals(SectionStatus.OK, result.status());
    assertEquals("UNAVAILABLE", result.usageState().name());
    assertEquals(1, result.activeSubscriptionCount());
    assertNull(result.successfulUsageCount());
    assertNull(result.lastObservedAt());
    assertEquals("UNKNOWN", result.usageWindowState());
    assertFalse(result.values().toString().contains("private"));
  }

  @Test void allUnreadableNeverBecomesEmpty() {
    when(subscriptions.listRecentActive(42L, product, 200)).thenThrow(new IllegalStateException());
    when(usage.list(42L, product, null, 200)).thenThrow(new IllegalStateException());
    assertEquals(SectionStatus.UNAVAILABLE, reader.read(product, 200).status());
    doThrow(new SecurityException()).when(subscriptions).listRecentActive(42L, product, 200);
    assertEquals(SectionStatus.PERMISSION_DENIED, reader.read(product, 200).status());
  }

  @Test void rejectsCrossProjectCrossProductInactiveOrOversizedRepositoryPayload() {
    when(usage.list(42L, product, null, 1)).thenReturn(List.of(observed(42, product, "9")));
    for (List<Subscription> rows : List.of(
        List.of(subscription(43, product, "9", SubscriptionStatus.ACTIVE)),
        List.of(subscription(42, ProductKey.parse("DATASET:102"), "9", SubscriptionStatus.ACTIVE)),
        List.of(subscription(42, product, "9", SubscriptionStatus.REVOKED)),
        List.of(subscription(42, product, "9", SubscriptionStatus.ACTIVE), subscription(42, product, "10", SubscriptionStatus.ACTIVE)))) {
      when(subscriptions.listRecentActive(42L, product, 1)).thenReturn(rows);
      var result = reader.read(product, 1);
      assertEquals("UNAVAILABLE", result.subscriptionState().name());
      assertNull(result.activeSubscriptionCount());
      assertEquals(1, result.successfulUsageCount());
      assertEquals(1, result.consumerCount());
    }
    when(usage.list(42L, product, null, 1)).thenReturn(List.of(observed(43, product, "9")));
    assertNull(reader.read(product, 1).successfulUsageCount());
  }

  @Test void missingProjectStopsBeforeAnyEvidenceRead() {
    when(project.requireProjectId()).thenThrow(new IllegalStateException("missing project"));
    assertThrows(IllegalStateException.class, () -> reader.read(product, 200));
    doReturn(null).when(project).requireProjectId();
    assertThrows(IllegalArgumentException.class, () -> reader.read(product, 200));
    verifyNoInteractions(subscriptions, usage);
  }
}
