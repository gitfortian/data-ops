package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.relationship.source.DatasetUsageEvidenceSynchronizer;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden contract: historical consumer evidence survives loss of the live owning consumer target. */
class DatasetGoldenConsumerTargetUnavailableTest {

  @Test
  void keepsStableConsumerRefWhenOwningTargetIsNoLongerResolvable() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DatasetUsageEvidenceSynchronizer synchronizer = mock(DatasetUsageEvidenceSynchronizer.class);
    when(synchronizer.synchronizeRecentByProduct(101L, 200)).thenReturn(List.of());
    when(currentProject.requireProjectId()).thenReturn(42L);

    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef historicalConsumer =
        new ConsumerRef(ConsumerType.JOB, "JOB", "job-77", "retired nightly export");
    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 16, 30);

    // The owning JOB object is deliberately not copied or resolved by Consumption. If that target
    // has been deleted or its provider is unavailable, the stable source identity and historical
    // Usage Evidence must still remain visible instead of being collapsed to "no consumers".
    when(subscriptions.list(42L, product, null)).thenReturn(List.of());
    when(usage.list(42L, product, null, 200)).thenReturn(List.of(new UsageEvidence(
        7L,
        42L,
        product,
        new SourceVersionRef("v4", "4"),
        historicalConsumer,
        observedAt,
        ConsumptionMode.QUERY,
        UsageOutcome.SUCCESS,
        "DATASET_QUERY_PERFORMANCE",
        "query-77",
        "dataset-101-query-77",
        observedAt)));

    ConsumerImpactView view =
        new ConsumerImpactService(subscriptions, usage, currentProject, synchronizer, null).view(product, 200);

    assertEquals(ConsumerImpactView.EvidenceState.EMPTY, view.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(1, view.consumers().size());

    ConsumerImpactView.KnownConsumer known = view.consumers().getFirst();
    assertEquals("JOB:JOB:job-77", known.consumerRef().identityKey());
    assertEquals("job-77", known.consumerRef().sourceIdentity());
    assertEquals(0, known.activeSubscriptionCount());
    assertEquals(1, known.successfulUsageCount());
    assertEquals(observedAt, known.lastObservedAt());
    assertTrue(known.providerEvidenceRefs().contains("DATASET_QUERY_PERFORMANCE:query-77"));
  }
}
