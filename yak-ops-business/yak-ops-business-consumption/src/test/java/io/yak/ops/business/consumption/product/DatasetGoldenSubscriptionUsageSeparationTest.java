package io.yak.ops.business.consumption.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.relationship.ConsumerImpactService;
import io.yak.ops.business.consumption.relationship.ConsumerImpactView;
import io.yak.ops.business.consumption.relationship.ConsumerRef;
import io.yak.ops.business.consumption.relationship.ConsumerType;
import io.yak.ops.business.consumption.relationship.ConsumptionMode;
import io.yak.ops.business.consumption.relationship.Subscription;
import io.yak.ops.business.consumption.relationship.SubscriptionRepository;
import io.yak.ops.business.consumption.relationship.SubscriptionService;
import io.yak.ops.business.consumption.relationship.SubscriptionStatus;
import io.yak.ops.business.consumption.relationship.UsageEvidence;
import io.yak.ops.business.consumption.relationship.UsageEvidenceRepository;
import io.yak.ops.business.consumption.relationship.UsageOutcome;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path A: declared Subscription and observed Usage remain independent evidence. */
class DatasetGoldenSubscriptionUsageSeparationTest {

  @Test
  void subscriptionAndObservedUsageRemainIndependentAndCancellationIsIdempotent() {
    long projectId = 7L;
    ProductKey productKey = new ProductKey(ProductType.DATASET, "42");
    ConsumerRef declaredConsumer =
        new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "alice", "Alice");
    ConsumerRef observedConsumer =
        new ConsumerRef(ConsumerType.USER, "SECURITY_PRINCIPAL", "bob", "Bob");

    CurrentProject currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(projectId);

    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    when(discovery.get(productKey))
        .thenReturn(ProductLookupResult.found(mock(DataProductView.class)));

    SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
    AtomicReference<Subscription> stored = new AtomicReference<>();
    when(subscriptions.find(projectId, productKey, declaredConsumer, ConsumptionMode.QUERY))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
    when(subscriptions.findById(projectId, 900L))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
    when(subscriptions.save(any(Subscription.class))).thenAnswer(invocation -> {
      Subscription candidate = invocation.getArgument(0);
      Subscription persisted = candidate.id() == null
          ? new Subscription(
              900L,
              candidate.projectId(),
              candidate.productKey(),
              candidate.consumerRef(),
              candidate.consumptionMode(),
              candidate.status(),
              candidate.createdBy(),
              candidate.createdAt(),
              candidate.updatedBy(),
              candidate.updatedAt())
          : candidate;
      stored.set(persisted);
      return persisted;
    });
    when(subscriptions.list(projectId, productKey, null)).thenAnswer(invocation -> {
      Subscription current = stored.get();
      return current == null ? List.of() : List.of(current);
    });

    SubscriptionService service = new SubscriptionService(subscriptions, discovery, currentProject);
    Subscription first =
        service.subscribe(productKey, declaredConsumer, ConsumptionMode.QUERY, "alice");
    Subscription duplicate =
        service.subscribe(productKey, declaredConsumer, ConsumptionMode.QUERY, "alice");

    assertEquals(900L, first.id());
    assertEquals(SubscriptionStatus.ACTIVE, first.status());
    assertSame(first, duplicate);

    LocalDateTime observedAt = LocalDateTime.of(2026, 9, 26, 12, 0);
    UsageEvidence observedUsage = new UsageEvidence(
        1001L,
        projectId,
        productKey,
        new SourceVersionRef("101", "v3"),
        observedConsumer,
        observedAt,
        ConsumptionMode.QUERY,
        UsageOutcome.SUCCESS,
        "DATASET_QUERY",
        "query:q-1",
        "DATASET_QUERY_PERFORMANCE:q-1",
        observedAt);

    UsageEvidenceRepository usage = mock(UsageEvidenceRepository.class);
    when(usage.list(projectId, productKey, null, 200)).thenReturn(List.of(observedUsage));

    ConsumerImpactService impactService = new ConsumerImpactService(subscriptions, usage, currentProject);
    ConsumerImpactView beforeCancel = impactService.view(productKey, 200);

    assertEquals(ConsumerImpactView.EvidenceState.READY, beforeCancel.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.READY, beforeCancel.usageState());
    assertEquals(2, beforeCancel.consumers().size());

    ConsumerImpactView.KnownConsumer alice = beforeCancel.consumers().stream()
        .filter(consumer -> consumer.consumerRef().identityKey().equals("USER:SECURITY_PRINCIPAL:alice"))
        .findFirst()
        .orElseThrow();
    assertEquals(1, alice.activeSubscriptionCount());
    assertEquals(0, alice.successfulUsageCount());

    ConsumerImpactView.KnownConsumer bob = beforeCancel.consumers().stream()
        .filter(consumer -> consumer.consumerRef().identityKey().equals("USER:SECURITY_PRINCIPAL:bob"))
        .findFirst()
        .orElseThrow();
    assertEquals(0, bob.activeSubscriptionCount());
    assertEquals(1, bob.successfulUsageCount());
    assertEquals(List.of("DATASET_QUERY:query:q-1"), bob.providerEvidenceRefs());

    Subscription cancelled = service.cancel(900L, "alice");
    Subscription cancelledAgain = service.cancel(900L, "alice");
    assertEquals(SubscriptionStatus.CANCELLED, cancelled.status());
    assertSame(cancelled, cancelledAgain);

    ConsumerImpactView afterCancel = impactService.view(productKey, 200);
    assertEquals(ConsumerImpactView.EvidenceState.READY, afterCancel.subscriptionState());
    assertEquals(ConsumerImpactView.EvidenceState.READY, afterCancel.usageState());
    assertEquals(1, afterCancel.consumers().size());
    assertEquals("USER:SECURITY_PRINCIPAL:bob",
        afterCancel.consumers().getFirst().consumerRef().identityKey());
    assertEquals(0, afterCancel.consumers().getFirst().activeSubscriptionCount());
    assertEquals(1, afterCancel.consumers().getFirst().successfulUsageCount());
  }
}
