package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.relationship.source.DataServiceUsageEvidenceSynchronizer;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Golden Path B contract from declared/observed Data Service evidence to known Consumer impact. */
class DataServiceGoldenConsumerImpactTest {

  @Test
  void mergesManagedConsumerSubscriptionAndObservedInvokeWithoutConflatingEvidence() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer synchronizer = mock(DataServiceUsageEvidenceSynchronizer.class);
    when(synchronizer.synchronizeRecentByProduct(7L, 200)).thenReturn(List.of());
    when(currentProject.requireProjectId()).thenReturn(3L);

    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    ConsumerRef consumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE,
        "DATA_SERVICE_CONSUMER",
        "21",
        "Golden BI Consumer");
    LocalDateTime subscribedAt = LocalDateTime.of(2026, 9, 26, 9, 5);
    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 9, 10);

    when(subscriptions.list(3L, product, null)).thenReturn(List.of(new Subscription(
        701L,
        3L,
        product,
        consumer,
        ConsumptionMode.API_INVOKE,
        SubscriptionStatus.ACTIVE,
        "owner",
        subscribedAt,
        "owner",
        subscribedAt)));
    when(usage.list(3L, product, null, 200)).thenReturn(List.of(new UsageEvidence(
        601L,
        3L,
        product,
        new SourceVersionRef("101", "r4"),
        consumer,
        observedAt,
        ConsumptionMode.API_INVOKE,
        UsageOutcome.SUCCESS,
        "DATA_SERVICE_INVOCATION",
        "invocation:501",
        "DATA_SERVICE_INVOCATION:501",
        observedAt)));

    ConsumerImpactView view =
        new ConsumerImpactService(subscriptions, usage, currentProject, null, synchronizer).view(product, 200);

    assertEquals(product, view.productKey());
    assertEquals(ConsumerImpactView.EvidenceState.READY, view.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(1, view.consumers().size());

    ConsumerImpactView.KnownConsumer known = view.consumers().getFirst();
    assertEquals("DATA_SERVICE:DATA_SERVICE_CONSUMER:21", known.consumerRef().identityKey());
    assertEquals(List.of(ConsumptionMode.API_INVOKE), known.declaredModes());
    assertEquals(List.of(ConsumptionMode.API_INVOKE), known.observedModes());
    assertEquals(1, known.activeSubscriptionCount());
    assertEquals(1, known.successfulUsageCount());
    assertEquals(1, known.observedVersions().size());
    assertEquals("101", known.observedVersions().getFirst().sourceVersion().identity());
    assertEquals("r4", known.observedVersions().getFirst().sourceVersion().displayVersion());
    assertEquals(1, known.observedVersions().getFirst().successfulUsageCount());
    assertEquals(subscribedAt, known.lastDeclaredAt());
    assertEquals(observedAt, known.lastObservedAt());
    assertTrue(known.providerEvidenceRefs()
        .contains("DATA_SERVICE_INVOCATION:invocation:501"));
  }

  @Test
  void subscriptionWithoutObservedInvokeRemainsDeclaredOnlyEvidence() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer synchronizer = mock(DataServiceUsageEvidenceSynchronizer.class);
    when(synchronizer.synchronizeRecentByProduct(7L, 200)).thenReturn(List.of());
    when(currentProject.requireProjectId()).thenReturn(3L);

    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    ConsumerRef consumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE,
        "DATA_SERVICE_CONSUMER",
        "21",
        "Golden BI Consumer");
    LocalDateTime subscribedAt = LocalDateTime.of(2026, 9, 26, 9, 5);

    when(subscriptions.list(3L, product, null)).thenReturn(List.of(new Subscription(
        701L,
        3L,
        product,
        consumer,
        ConsumptionMode.API_INVOKE,
        SubscriptionStatus.ACTIVE,
        "owner",
        subscribedAt,
        "owner",
        subscribedAt)));
    when(usage.list(3L, product, null, 200)).thenReturn(List.of());

    ConsumerImpactView view =
        new ConsumerImpactService(subscriptions, usage, currentProject, null, synchronizer).view(product, 200);

    assertEquals(ConsumerImpactView.EvidenceState.READY, view.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.EMPTY, view.usageState());
    assertEquals(1, view.consumers().size());

    ConsumerImpactView.KnownConsumer known = view.consumers().getFirst();
    assertEquals("DATA_SERVICE:DATA_SERVICE_CONSUMER:21", known.consumerRef().identityKey());
    assertEquals(List.of(ConsumptionMode.API_INVOKE), known.declaredModes());
    assertEquals(List.of(), known.observedModes());
    assertEquals(1, known.activeSubscriptionCount());
    assertEquals(0, known.successfulUsageCount());
    assertTrue(known.observedVersions().isEmpty());
    assertEquals(subscribedAt, known.lastDeclaredAt());
    assertEquals(null, known.lastObservedAt());
    assertEquals(List.of(), known.providerEvidenceRefs());
  }

  @Test
  void observedConsumerRemainsKnownWhenLiveConsumerOrDeclarationIsNoLongerAvailable() {
    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DataServiceUsageEvidenceSynchronizer synchronizer = mock(DataServiceUsageEvidenceSynchronizer.class);
    when(synchronizer.synchronizeRecentByProduct(7L, 200)).thenReturn(List.of());
    when(currentProject.requireProjectId()).thenReturn(3L);

    ProductKey product = ProductKey.parse("DATA_SERVICE:7");
    ConsumerRef historicalConsumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE,
        "DATA_SERVICE_CONSUMER",
        "21",
        "Golden BI Consumer");
    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 9, 10);

    // The live declaration/target may already be removed or unavailable. Impact is evidence-driven:
    // historical successful Usage Evidence keeps the stable ConsumerRef instead of becoming fake zero usage.
    when(subscriptions.list(3L, product, null)).thenReturn(List.of());
    when(usage.list(3L, product, null, 200)).thenReturn(List.of(new UsageEvidence(
        601L,
        3L,
        product,
        new SourceVersionRef("101", "r4"),
        historicalConsumer,
        observedAt,
        ConsumptionMode.API_INVOKE,
        UsageOutcome.SUCCESS,
        "DATA_SERVICE_INVOCATION",
        "invocation:501",
        "DATA_SERVICE_INVOCATION:501",
        observedAt)));

    ConsumerImpactView view =
        new ConsumerImpactService(subscriptions, usage, currentProject, null, synchronizer).view(product, 200);

    assertEquals(ConsumerImpactView.EvidenceState.EMPTY, view.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.READY, view.usageState());
    assertEquals(1, view.consumers().size());

    ConsumerImpactView.KnownConsumer known = view.consumers().getFirst();
    assertEquals("DATA_SERVICE:DATA_SERVICE_CONSUMER:21", known.consumerRef().identityKey());
    assertEquals(List.of(), known.declaredModes());
    assertEquals(List.of(ConsumptionMode.API_INVOKE), known.observedModes());
    assertEquals(0, known.activeSubscriptionCount());
    assertEquals(1, known.successfulUsageCount());
    assertEquals("101", known.observedVersions().getFirst().sourceVersion().identity());
    assertEquals(observedAt, known.lastObservedAt());
    assertTrue(known.providerEvidenceRefs()
        .contains("DATA_SERVICE_INVOCATION:invocation:501"));
  }
}
