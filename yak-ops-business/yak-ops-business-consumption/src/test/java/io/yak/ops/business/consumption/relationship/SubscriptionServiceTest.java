package io.yak.ops.business.consumption.relationship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionServiceTest {

  private final InMemorySubscriptionRepository repository = new InMemorySubscriptionRepository();
  private ProductDiscoveryService discovery;
  private CurrentProject currentProject;
  private SubscriptionService service;

  @BeforeEach
  void setUp() {
    repository.rows.clear();
    repository.nextId = 1L;
    discovery = mock(ProductDiscoveryService.class);
    currentProject = mock(CurrentProject.class);
    when(currentProject.requireProjectId()).thenReturn(42L);
    when(discovery.get(any())).thenReturn(ProductLookupResult.found(mock(DataProductView.class)));
    service = new SubscriptionService(repository, discovery, currentProject);
  }

  @Test
  void duplicateSubscribeReturnsSameActiveRelationship() {
    ProductKey product = ProductKey.parse("DATASET:101");
    ConsumerRef firstRef = new ConsumerRef(ConsumerType.DASHBOARD, "DASHBOARD", "9001", "Revenue v1");

    Subscription first = service.subscribe(product, firstRef, ConsumptionMode.QUERY, "alice");
    Subscription duplicate = service.subscribe(
        product,
        new ConsumerRef(ConsumerType.DASHBOARD, "DASHBOARD", "9001", "Renamed dashboard"),
        ConsumptionMode.QUERY,
        "bob");

    assertEquals(1, repository.rows.size());
    assertEquals(first.id(), duplicate.id());
    assertEquals(SubscriptionStatus.ACTIVE, duplicate.status());
    assertEquals("alice", duplicate.createdBy());
  }

  @Test
  void duplicateCancelIsIdempotent() {
    Subscription created = service.subscribe(
        ProductKey.parse("DATA_SERVICE:7"),
        new ConsumerRef(ConsumerType.DATA_SERVICE, "DATA_SERVICE_CONSUMER", "33", "risk-engine"),
        ConsumptionMode.API_INVOKE,
        "alice");

    Subscription cancelled = service.cancel(created.id(), "alice");
    Subscription duplicate = service.cancel(created.id(), "bob");

    assertEquals(SubscriptionStatus.CANCELLED, duplicate.status());
    assertEquals(cancelled.updatedBy(), duplicate.updatedBy());
    assertEquals(cancelled.updatedAt(), duplicate.updatedAt());
    assertEquals(1, repository.rows.size());
  }

  @Test
  void resubscribeReactivatesSameRelationshipIdentity() {
    Subscription created = service.subscribe(
        ProductKey.parse("DATASET:202"),
        new ConsumerRef(ConsumerType.JOB, "WORKFLOW_JOB", "job-12", "nightly"),
        ConsumptionMode.DOWNSTREAM,
        "alice");
    service.cancel(created.id(), "alice");

    Subscription reactivated = service.subscribe(
        created.productKey(),
        new ConsumerRef(ConsumerType.JOB, "WORKFLOW_JOB", "job-12", "nightly-v2"),
        ConsumptionMode.DOWNSTREAM,
        "carol");

    assertEquals(created.id(), reactivated.id());
    assertEquals(created.createdAt(), reactivated.createdAt());
    assertEquals(SubscriptionStatus.ACTIVE, reactivated.status());
    assertEquals("carol", reactivated.updatedBy());
    assertEquals("nightly-v2", reactivated.consumerRef().displayHint());
    assertEquals(1, repository.rows.size());
  }

  @Test
  void providerUnavailableNeverCreatesSubscription() {
    when(discovery.get(any())).thenReturn(ProductLookupResult.unavailable("dataset provider timeout"));

    assertThrows(IllegalStateException.class, () -> service.subscribe(
        ProductKey.parse("DATASET:303"),
        new ConsumerRef(ConsumerType.USER, "SECURITY_USER", "u-1", "Alice"),
        ConsumptionMode.QUERY,
        "alice"));
    assertEquals(0, repository.rows.size());
  }

  @Test
  void consumerDisplayHintDoesNotParticipateInStableIdentity() {
    ConsumerRef before = new ConsumerRef(ConsumerType.TEAM, "SECURITY_TEAM", "team-9", "Finance");
    ConsumerRef after = new ConsumerRef(ConsumerType.TEAM, "security_team", "team-9", "Finance Renamed");
    assertEquals(before.identityKey(), after.identityKey());
  }

  private static final class InMemorySubscriptionRepository implements SubscriptionRepository {
    private final List<Subscription> rows = new ArrayList<>();
    private long nextId = 1L;

    @Override
    public Optional<Subscription> find(
        Long projectId,
        ProductKey productKey,
        ConsumerRef consumerRef,
        ConsumptionMode consumptionMode) {
      return rows.stream().filter(row ->
          row.projectId().equals(projectId)
              && row.productKey().equals(productKey)
              && sameConsumer(row.consumerRef(), consumerRef)
              && row.consumptionMode() == consumptionMode).findFirst();
    }

    @Override
    public Optional<Subscription> findById(Long projectId, Long subscriptionId) {
      return rows.stream().filter(row ->
          row.projectId().equals(projectId) && row.id().equals(subscriptionId)).findFirst();
    }

    @Override
    public Subscription save(Subscription subscription) {
      Subscription saved = subscription;
      if (subscription.id() == null) {
        saved = new Subscription(
            nextId++,
            subscription.projectId(),
            subscription.productKey(),
            subscription.consumerRef(),
            subscription.consumptionMode(),
            subscription.status(),
            subscription.createdBy(),
            subscription.createdAt(),
            subscription.updatedBy(),
            subscription.updatedAt());
      }
      final Long savedId = saved.id();
      rows.removeIf(row -> row.id().equals(savedId));
      rows.add(saved);
      return saved;
    }

    @Override
    public List<Subscription> list(Long projectId, ProductKey productKey, ConsumerRef consumerRef) {
      return rows.stream().filter(row -> row.projectId().equals(projectId))
          .filter(row -> productKey == null || row.productKey().equals(productKey))
          .filter(row -> consumerRef == null || sameConsumer(row.consumerRef(), consumerRef))
          .toList();
    }

    private static boolean sameConsumer(ConsumerRef left, ConsumerRef right) {
      return left.consumerType() == right.consumerType()
          && left.sourceDomain().equals(right.sourceDomain())
          && left.sourceIdentity().equals(right.sourceIdentity());
    }
  }
}
