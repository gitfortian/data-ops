package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.dataservice.access.ConsumerView;
import io.yak.ops.business.dataservice.access.DataServiceConsumerManager;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Phase 4 #104 Golden Path B: Data Service subscription/revoke operations are idempotent. */
class DataServiceGoldenSubscriptionIdempotencyTest {

  @Test
  void duplicateSubscribeAndRevokeKeepOneStableRelationshipTruth() {
    ProductKey product = ProductKey.parse("DATA_SERVICE:88");
    ConsumerRef consumer = new ConsumerRef(
        ConsumerType.DATA_SERVICE,
        "DATA_SERVICE_CONSUMER",
        "21",
        "Golden BI Consumer");

    SubscriptionRepository repository = mock(SubscriptionRepository.class);
    ProductDiscoveryService discovery = mock(ProductDiscoveryService.class);
    CurrentProject currentProject = mock(CurrentProject.class);
    DataServiceConsumerManager dataServiceConsumers = mock(DataServiceConsumerManager.class);
    ActionAuthorization actionAuthorization = mock(ActionAuthorization.class);
    AtomicReference<Subscription> stored = new AtomicReference<>();

    when(currentProject.requireProjectId()).thenReturn(3L);
    when(dataServiceConsumers.get(21L)).thenReturn(mock(ConsumerView.class));
    when(discovery.get(product)).thenReturn(ProductLookupResult.found(mock(DataProductView.class)));
    when(repository.find(3L, product, consumer, ConsumptionMode.API_INVOKE))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
    when(repository.findById(3L, 701L))
        .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
    when(repository.save(any())).thenAnswer(invocation -> {
      Subscription candidate = invocation.getArgument(0);
      Subscription saved = candidate.id() == null
          ? new Subscription(
              701L,
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
      stored.set(saved);
      return saved;
    });

    SubscriptionService service = new SubscriptionService(
        repository, discovery, currentProject, dataServiceConsumers, actionAuthorization);

    Subscription created = service.subscribe(product, consumer, ConsumptionMode.API_INVOKE, "alice");
    Subscription duplicate = service.subscribe(product, consumer, ConsumptionMode.API_INVOKE, "bob");

    assertEquals(701L, created.id());
    assertEquals(created.id(), duplicate.id());
    assertEquals(SubscriptionStatus.ACTIVE, duplicate.status());
    assertEquals("alice", duplicate.createdBy());
    assertEquals(created.createdAt(), duplicate.createdAt());
    verify(repository, times(1)).save(any());

    Subscription cancelled = service.cancel(created.id(), "alice");
    Subscription duplicateCancel = service.cancel(created.id(), "bob");

    assertEquals(created.id(), cancelled.id());
    assertEquals(cancelled.id(), duplicateCancel.id());
    assertEquals(SubscriptionStatus.REVOKED, duplicateCancel.status());
    assertEquals("alice", duplicateCancel.updatedBy());
    assertEquals(cancelled.updatedAt(), duplicateCancel.updatedAt());
    verify(repository, times(2)).save(any());
  }
}
