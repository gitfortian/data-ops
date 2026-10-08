package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.business.consumption.relationship.source.DataServiceUsageEvidenceSynchronizer;
import io.yak.ops.business.consumption.relationship.source.DatasetUsageEvidenceSynchronizer;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsumerImpactServiceTest {

  @Test
  void keepsDeclaredAndObservedEvidenceDistinctWhileMergingSameConsumer() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer datasetSynchronizer = mock(DatasetUsageEvidenceSynchronizer.class);
    when(datasetSynchronizer.synchronizeRecentByProduct(101L, 200)).thenReturn(List.of());
    when(currentProject.requireProjectId()).thenReturn(42L);

    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.DASHBOARD, "DASHBOARD", "9", "Revenue");
    LocalDateTime declaredAt = LocalDateTime.of(2026, 9, 25, 9, 0);
    LocalDateTime observedAt = declaredAt.plusHours(1);

    when(subscriptions.list(42L, product, null)).thenReturn(List.of(new Subscription(
        1L, 42L, product, consumer, ConsumptionMode.QUERY, SubscriptionStatus.ACTIVE,
        "alice", declaredAt, "alice", declaredAt)));
    when(usage.list(42L, product, null, 200)).thenReturn(List.of(new UsageEvidence(
        2L, 42L, product, new SourceVersionRef("v3", "3"), consumer, observedAt,
        ConsumptionMode.QUERY, UsageOutcome.SUCCESS, "DATASET_QUERY_PERFORMANCE", "query-7", "d-7", observedAt)));

    ConsumerImpactView view = new ConsumerImpactService(
        subscriptions, usage, currentProject, datasetSynchronizer, null).view(product, 200);

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(1, view.consumers().size());
    assertEquals(1, view.consumers().getFirst().activeSubscriptionCount());
    assertEquals(1, view.consumers().getFirst().successfulUsageCount());
    assertEquals(observedAt, view.consumers().getFirst().lastObservedAt());
    assertTrue(view.consumers().getFirst().providerEvidenceRefs().contains("DATASET_QUERY_PERFORMANCE:query-7"));
  }

  @Test
  void showsExactDatasetVersionsAndEvidencePerKnownConsumerWithoutInventingDeclarations() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "alice", "Alice");
    LocalDateTime first = LocalDateTime.of(2026, 10, 8, 8, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(sync.synchronizeRecentByProduct(101L, 4)).thenReturn(List.of());
    when(usage.list(42L, product, null, 4)).thenReturn(List.of(
        event(42L, product, consumer, "9001", "v1", first, "query:a"),
        event(42L, product, consumer, "9002", "v2", first.plusHours(1), "query:b"),
        event(42L, product, consumer, "9001", "v1", first.plusHours(2), "query:c")));

    ConsumerImpactView view =
        new ConsumerImpactService(subscriptions, usage, project, sync, null).view(product, 4);

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(ConsumerImpactView.EvidenceState.EMPTY, view.subscriptionState());
    assertEquals(1, view.consumers().size());
    var known = view.consumers().getFirst();
    assertEquals(3, known.successfulUsageCount());
    assertEquals(0, known.activeSubscriptionCount());
    assertEquals(2, known.observedVersions().size());
    var latest = known.observedVersions().getFirst();
    assertEquals("9001", latest.sourceVersion().identity());
    assertEquals("v1", latest.sourceVersion().displayVersion());
    assertEquals(2, latest.successfulUsageCount());
    assertEquals(first.plusHours(2), latest.lastObservedAt());
    assertEquals(List.of("DATASET_QUERY_PERFORMANCE:query:a", "DATASET_QUERY_PERFORMANCE:query:c"),
        latest.providerEvidenceRefs());
    assertEquals("9002", known.observedVersions().get(1).sourceVersion().identity());
    assertEquals(1, known.observedVersions().get(1).successfulUsageCount());
  }

  @Test
  void partialSourceRecoveryKeepsActualVersionEvidenceButDoesNotClaimCompleteCoverage() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "alice", "Alice");
    LocalDateTime at = LocalDateTime.of(2026, 10, 8, 10, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(sync.synchronizeRecentByProduct(101L, 20))
        .thenReturn(List.of(UsageNormalizationResult.gap("query:legacy", "missing exact version")));
    when(usage.list(42L, product, null, 20))
        .thenReturn(List.of(event(42L, product, consumer, "9001", null, at, "query:a")));

    ConsumerImpactView view = new ConsumerImpactService(subscriptions, usage, project, sync, null)
        .view(product, 20);

    assertEquals(ConsumerImpactView.EvidenceState.UNAVAILABLE, view.usageState());
    assertTrue(view.coverageNote().contains("partial"));
    assertEquals(1, view.coverage().normalizationGapCount());
    assertEquals(false, view.coverage().sourceReadUnavailable());
    assertEquals("9001", view.consumers().getFirst().observedVersions().getFirst()
        .sourceVersion().identity());
    assertEquals(1, view.consumers().getFirst().successfulUsageCount());
  }

  @Test
  void fullSourceAuditWindowIsBoundedReadableEvidenceNotProviderFailure() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer synchronizer = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "alice", "Alice");
    LocalDateTime at = LocalDateTime.of(2026, 10, 8, 10, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(synchronizer.synchronizeRecentByProduct(101L, 2)).thenReturn(List.of(
        UsageNormalizationResult.ignored("query:a", "not an observed consumer"),
        UsageNormalizationResult.ignored("query:b", "not an observed consumer")));
    when(usage.list(42L, product, null, 2)).thenReturn(List.of(
        event(42L, product, consumer, "9007199254740993", "r8", at, "query:known")));

    ConsumerImpactView view = new ConsumerImpactService(
        subscriptions, usage, project, synchronizer, null).view(product, 2);

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(2, view.coverage().requestedUsageLimit());
    assertEquals(2, view.coverage().sourceRecordCount());
    assertEquals(1, view.coverage().normalizedUsageCount());
    assertTrue(view.coverage().sourceWindowLimitReached());
    assertEquals(false, view.coverage().normalizedUsageWindowLimitReached());
    assertEquals(false, view.coverage().sourceReadUnavailable());
    assertTrue(view.coverageNote().contains("row limit was reached"));
    assertEquals("9007199254740993",
        view.consumers().getFirst().observedVersions().getFirst().sourceVersion().identity());
  }

  @Test
  void normalizedUsageWindowLimitIsVisibleWithoutDiscardingObservedRows() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer synchronizer = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "bob", "Bob");
    LocalDateTime at = LocalDateTime.of(2026, 10, 8, 10, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(synchronizer.synchronizeRecentByProduct(101L, 2)).thenReturn(List.of());
    when(usage.list(42L, product, null, 2)).thenReturn(List.of(
        event(42L, product, consumer, "9001", "v1", at, "query:1"),
        event(42L, product, consumer, "9002", "v2", at.plusMinutes(1), "query:2")));

    ConsumerImpactView view = new ConsumerImpactService(
        subscriptions, usage, project, synchronizer, null).view(product, 2);

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(false, view.coverage().sourceWindowLimitReached());
    assertTrue(view.coverage().normalizedUsageWindowLimitReached());
    assertEquals(2, view.coverage().normalizedUsageCount());
    assertTrue(view.coverageNote().contains("row limit was reached"));
    assertEquals(2, view.consumers().getFirst().observedVersions().size());
  }

  private static UsageEvidence event(
      Long projectId, ProductKey product, ConsumerRef consumer, String versionId,
      String displayVersion, LocalDateTime at, String evidenceRef) {
    return new UsageEvidence(
        null, projectId, product, new SourceVersionRef(versionId, displayVersion),
        consumer, at, ConsumptionMode.QUERY, UsageOutcome.SUCCESS,
        "DATASET_QUERY_PERFORMANCE", evidenceRef, evidenceRef, at);
  }

  @Test
  void persistedDatasetAuditOutageKeepsKnownUsageButDoesNotClaimCompleteCoverage() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer synchronizer = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "alice", "Alice");
    LocalDateTime observedAt = LocalDateTime.of(2026, 10, 8, 12, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(synchronizer.synchronizeRecentByProduct(101L, 200))
        .thenThrow(new IllegalStateException("source audit store offline"));
    when(usage.list(42L, product, null, 200)).thenReturn(
        List.of(event(42L, product, consumer, "9001", "v1", observedAt, "query:known")));

    ConsumerImpactView view = new ConsumerImpactService(
        subscriptions, usage, project, synchronizer, null).view(product, 200);

    assertEquals(ConsumerImpactView.EvidenceState.UNAVAILABLE, view.usageState());
    assertEquals(ConsumerImpactView.EvidenceState.EMPTY, view.subscriptionState());
    assertTrue(view.coverage().sourceReadUnavailable());
    assertEquals(0, view.coverage().normalizationGapCount());
    assertTrue(view.coverageNote().contains("partial"));
    assertEquals(1, view.consumers().size());
    assertEquals(1, view.consumers().getFirst().successfulUsageCount());
    assertEquals("9001",
        view.consumers().getFirst().observedVersions().getFirst().sourceVersion().identity());
  }

  @Test
  void providerFailureIsUnavailableNotFakeEmpty() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer dataServiceSynchronizer = mock(DataServiceUsageEvidenceSynchronizer.class);
    when(dataServiceSynchronizer.synchronizeRecentByProduct(7L, 50)).thenReturn(List.of());
    when(currentProject.requireProjectId()).thenReturn(42L);
    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    when(subscriptions.list(42L, product, null)).thenThrow(new IllegalStateException("db down"));
    when(usage.list(42L, product, null, 50)).thenReturn(List.of());

    ConsumerImpactView view = new ConsumerImpactService(
        subscriptions, usage, currentProject, null, dataServiceSynchronizer).view(product, 50);

    assertEquals(ConsumerImpactView.EvidenceState.UNAVAILABLE, view.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.EMPTY, view.usageState());
    assertTrue(view.coverageNote().contains("partial"));
  }
}
