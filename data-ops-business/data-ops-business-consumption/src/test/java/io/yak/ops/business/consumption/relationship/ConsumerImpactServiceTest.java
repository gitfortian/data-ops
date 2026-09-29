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
