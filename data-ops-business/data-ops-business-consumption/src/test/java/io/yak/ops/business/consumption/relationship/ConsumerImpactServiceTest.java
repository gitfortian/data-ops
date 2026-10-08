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

  @Test
  void cursorRecoveryReturnsExactDatasetPageAndContinuationWithoutTouchingDeclaredSubscriptions() {
    SubscriptionRepository subs = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey key = ProductKey.parse("DATASET:101");
    when(project.requireProjectId()).thenReturn(42L);
    UsageEvidence existing = mock(UsageEvidence.class);
    when(existing.providerEvidenceRef()).thenReturn("query:old-audit");
    // Build the normalization result before starting another Mockito stubbing:
    // normalized(existing) invokes a mocked getter.
    UsageNormalizationResult successful = UsageNormalizationResult.normalized(existing);
    when(sync.recoverSuccessfulVersionPage(101L, 9007199254740993L, null, 200))
        .thenReturn(new DatasetUsageEvidenceSynchronizer.DatasetRecoveryPage(
            List.of(successful), 701L, false));
    var service = new ConsumerImpactService(subs, usage, project, sync, null);

    var result = service.recoverDatasetVersionPage(key, "9007199254740993", null, 999);

    assertEquals("DATASET:101", result.productKey());
    assertEquals("9007199254740993", result.sourceVersionIdentity());
    assertEquals(200, result.requestedLimit());
    assertEquals(1, result.visitedAuditCount());
    assertEquals(1, result.normalizedOrAlreadyPresentCount());
    assertEquals(701L, result.nextBeforeAuditId());
    assertEquals(false, result.retainedAuditExhausted());
    assertEquals(false, result.retryRequired());
    org.mockito.Mockito.verify(sync).recoverSuccessfulVersionPage(101L, 9007199254740993L, null, 200);
    org.mockito.Mockito.verifyNoInteractions(subs, usage);
  }

  @Test
  void gapsAndUnavailableNormalizationBlockCursorAdvanceUntilSamePageCanBeRetried() {
    SubscriptionRepository subs = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    when(project.requireProjectId()).thenReturn(42L);
    var page = new DatasetUsageEvidenceSynchronizer.DatasetRecoveryPage(
        List.of(
            UsageNormalizationResult.gap("query:old", "subject absent"),
            UsageNormalizationResult.unavailable("query:recent", "store failure"),
            UsageNormalizationResult.ignored("query:bad", "cannot count as success")),
        500L, false);
    when(sync.recoverSuccessfulVersionPage(101L, 7L, 501L, 10)).thenReturn(page);

    var result = new ConsumerImpactService(subs, usage, project, sync, null)
        .recoverDatasetVersionPage(ProductKey.parse("DATASET:101"), "7", 501L, 10);

    assertEquals(3, result.visitedAuditCount());
    assertEquals(0, result.normalizedOrAlreadyPresentCount());
    assertEquals(2, result.normalizationGapCount());
    assertEquals(1, result.normalizationUnavailableCount());
    assertEquals(null, result.nextBeforeAuditId());
    assertTrue(result.retryRequired());
    assertEquals(false, result.retainedAuditExhausted());
    assertEquals(501L, result.requestedBeforeAuditId());
  }

  @Test
  void emptyRetainedAuditPageIsExplicitlyExhaustedWithoutClaimingAllHistoricalTime() {
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    when(project.requireProjectId()).thenReturn(42L);
    when(sync.recoverSuccessfulVersionPage(101L, 7L, 20L, 50))
        .thenReturn(new DatasetUsageEvidenceSynchronizer.DatasetRecoveryPage(
            List.of(), null, true));
    var result = new ConsumerImpactService(
        mock(SubscriptionRepository.class), mock(UsageEvidenceRepository.class),
        project, sync, null)
        .recoverDatasetVersionPage(ProductKey.parse("DATASET:101"), "7", 20L, 50);

    assertTrue(result.retainedAuditExhausted());
    assertEquals(null, result.nextBeforeAuditId());
    assertEquals(false, result.retryRequired());
  }

  @Test
  void invalidRecoveryIdentityAndCursorNeverReadProjectOrPersistedUsage() {
    SubscriptionRepository subs = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    var service = new ConsumerImpactService(subs, usage, project, sync, null);

    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.recoverDatasetVersionPage(ProductKey.parse("DATA_SERVICE:101"), "7", null, 20));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.recoverDatasetVersionPage(ProductKey.parse("DATASET:101"), "v1", null, 20));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.recoverDatasetVersionPage(ProductKey.parse("DATASET:101"), "07", null, 20));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.recoverDatasetVersionPage(ProductKey.parse("DATASET:101"), "7", 0L, 20));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.recoverDatasetVersionPage(ProductKey.parse("DATASET:0"), "7", null, 20));
    org.mockito.Mockito.verifyNoInteractions(project, sync, subs, usage);
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
  @Test
  void oldImmutableRevisionUsesProjectScopedPersistedUsageInsteadOfLatestProductWindow() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer synchronizer = mock(DataServiceUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    ConsumerRef consumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE, "DATA_SERVICE_CONSUMER", "21", "Historic client");
    LocalDateTime historical = LocalDateTime.of(2026, 7, 1, 9, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(synchronizer.synchronizeRecentByProduct(7L, 200)).thenReturn(List.of());
    when(usage.listByVersion(42L, product, "9007199254740993", 200)).thenReturn(List.of(
        new UsageEvidence(
            555L, 42L, product, new SourceVersionRef("9007199254740993", "r1"),
            consumer, historical, ConsumptionMode.API_INVOKE, UsageOutcome.SUCCESS,
            "DATA_SERVICE_INVOCATION", "invocation:555",
            "DATA_SERVICE_INVOCATION:555", historical)));

    var view = new ConsumerImpactService(
        subscriptions, usage, project, null, synchronizer)
        .view(product, 200, "9007199254740993");

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(1, view.consumers().size());
    assertEquals("9007199254740993", view.consumers().getFirst()
        .observedVersions().getFirst().sourceVersion().identity());
    assertEquals(1, view.consumers().getFirst().successfulUsageCount());
    assertTrue(view.coverageNote().contains("Exact immutable version 9007199254740993"));
    org.mockito.Mockito.verify(usage).listByVersion(42L, product, "9007199254740993", 200);
    org.mockito.Mockito.verify(usage, org.mockito.Mockito.never())
        .list(42L, product, null, 200);
  }

  @Test
  void exactDatasetVersionRecoversItsOwnOldSourceBeforeReadingPersistedUsage() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer synchronizer = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef consumer = new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "alice", "Alice");
    LocalDateTime observedAt = LocalDateTime.of(2025, 7, 1, 9, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(synchronizer.synchronizeRecentByProductAndVersion(101L, 9007199254740993L, 200))
        .thenReturn(List.of());
    when(usage.listByVersion(42L, product, "9007199254740993", 200))
        .thenReturn(List.of(event(42L, product, consumer, "9007199254740993",
            "v1", observedAt, "query:old")));

    var view = new ConsumerImpactService(subscriptions, usage, project, synchronizer, null)
        .view(product, 200, "9007199254740993");

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals("9007199254740993", view.consumers().getFirst()
        .observedVersions().getFirst().sourceVersion().identity());
    assertTrue(view.coverageNote().contains("scoped to this exact DatasetVersion"));
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(synchronizer, usage);
    order.verify(synchronizer).synchronizeRecentByProductAndVersion(101L, 9007199254740993L, 200);
    order.verify(usage).listByVersion(42L, product, "9007199254740993", 200);
    org.mockito.Mockito.verify(synchronizer, org.mockito.Mockito.never())
        .synchronizeRecentByProduct(101L, 200);
  }

  @Test
  void invalidExactDatasetVersionPreservesPersistedEvidenceButSignalsSourceGap() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer sync = mock(DatasetUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATASET:101");
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(usage.listByVersion(42L, product, "display-only", 200)).thenReturn(List.of());
    var view = new ConsumerImpactService(subscriptions, usage, project, sync, null)
        .view(product, 200, "display-only");

    assertEquals(ConsumerImpactView.EvidenceState.UNAVAILABLE, view.usageState());
    assertTrue(view.coverage().sourceReadUnavailable());
    org.mockito.Mockito.verifyNoInteractions(sync);
    org.mockito.Mockito.verify(usage).listByVersion(42L, product, "display-only", 200);
  }

  @Test
  void invalidRequestedVersionNeverQueriesProjectOrUsage() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    var service = new ConsumerImpactService(subscriptions, usage, project);
    ProductKey product = ProductKey.parse("DATASET:101");

    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.view(product, 200, " "));
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> service.view(product, 200, "a".repeat(129)));
    org.mockito.Mockito.verifyNoInteractions(project, usage, subscriptions);
  }

  @Test
  void exactDataServiceRevisionReconcilesItsOwnSourceBeforeReadingPersistedUsage() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer synchronizer = mock(DataServiceUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    ConsumerRef consumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE, "DATA_SERVICE_CONSUMER", "21", "Historic client");
    LocalDateTime observedAt = LocalDateTime.of(2025, 7, 1, 9, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(synchronizer.synchronizeRecentByProductAndRevision(
        7L, 9007199254740995L, 200)).thenReturn(List.of());
    when(usage.listByVersion(42L, product, "9007199254740995", 200))
        .thenReturn(List.of(new UsageEvidence(
            55L, 42L, product, new SourceVersionRef("9007199254740995", "r1"),
            consumer, observedAt, ConsumptionMode.API_INVOKE, UsageOutcome.SUCCESS,
            "DATA_SERVICE_INVOCATION", "invocation:55",
            "DATA_SERVICE_INVOCATION:55", observedAt)));

    var view = new ConsumerImpactService(subscriptions, usage, project, null, synchronizer)
        .view(product, 200, "9007199254740995");

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(1, view.consumers().getFirst().successfulUsageCount());
    assertEquals("9007199254740995", view.consumers().getFirst()
        .observedVersions().getFirst().sourceVersion().identity());
    assertTrue(view.coverageNote().contains("scoped to this exact revision"));
    org.mockito.InOrder order = org.mockito.Mockito.inOrder(synchronizer, usage);
    order.verify(synchronizer).synchronizeRecentByProductAndRevision(
        7L, 9007199254740995L, 200);
    order.verify(usage).listByVersion(42L, product, "9007199254740995", 200);
    org.mockito.Mockito.verify(synchronizer, org.mockito.Mockito.never())
        .synchronizeRecentByProduct(7L, 200);
  }

  @Test
  void invalidSourceRevisionKeepsAvailablePersistedRowsButSignalsIncompleteSource() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject project = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer sync = mock(DataServiceUsageEvidenceSynchronizer.class);
    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    LocalDateTime observedAt = LocalDateTime.of(2025, 7, 1, 9, 0);
    when(project.requireProjectId()).thenReturn(42L);
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(usage.listByVersion(42L, product, "r-legacy", 200)).thenReturn(List.of(
        event(42L, product,
            new ConsumerRef(ConsumerType.DATA_SERVICE, "DATA_SERVICE_CONSUMER", "11", null),
            "r-legacy", "legacy", observedAt, "invocation:11")));

    var view = new ConsumerImpactService(subscriptions, usage, project, null, sync)
        .view(product, 200, "r-legacy");

    assertTrue(view.coverage().sourceReadUnavailable());
    assertEquals(1, view.consumers().size());
    org.mockito.Mockito.verifyNoInteractions(sync);
  }

}
