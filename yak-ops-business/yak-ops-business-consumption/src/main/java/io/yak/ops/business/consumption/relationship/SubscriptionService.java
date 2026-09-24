package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.core.project.CurrentProject;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns declared consumer dependency truth. Subscription never changes source access policy. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class SubscriptionService {

  private final SubscriptionRepository repository;
  private final ProductDiscoveryService productDiscovery;
  private final CurrentProject currentProject;
  private final Clock clock = Clock.systemDefaultZone();

  @Transactional
  public Subscription subscribe(
      ProductKey productKey,
      ConsumerRef consumerRef,
      ConsumptionMode consumptionMode,
      String actor) {
    Long projectId = currentProject.requireProjectId();
    String principal = requireActor(actor);
    requireDiscoverable(productKey);

    Subscription existing = repository.find(projectId, productKey, consumerRef, consumptionMode)
        .orElse(null);
    if (existing != null && existing.status() == SubscriptionStatus.ACTIVE) {
      return existing;
    }

    LocalDateTime now = LocalDateTime.now(clock);
    if (existing != null) {
      return repository.save(new Subscription(
          existing.id(),
          existing.projectId(),
          existing.productKey(),
          new ConsumerRef(
              existing.consumerRef().consumerType(),
              existing.consumerRef().sourceDomain(),
              existing.consumerRef().sourceIdentity(),
              consumerRef.displayHint()),
          existing.consumptionMode(),
          SubscriptionStatus.ACTIVE,
          existing.createdBy(),
          existing.createdAt(),
          principal,
          now));
    }

    try {
      return repository.save(new Subscription(
          null,
          projectId,
          productKey,
          consumerRef,
          consumptionMode,
          SubscriptionStatus.ACTIVE,
          principal,
          now,
          principal,
          now));
    } catch (DuplicateKeyException race) {
      return repository.find(projectId, productKey, consumerRef, consumptionMode)
          .orElseThrow(() -> race);
    }
  }

  @Transactional
  public Subscription cancel(Long subscriptionId, String actor) {
    Long projectId = currentProject.requireProjectId();
    String principal = requireActor(actor);
    Subscription existing = repository.findById(projectId, subscriptionId)
        .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
    if (existing.status() == SubscriptionStatus.CANCELLED) {
      return existing;
    }
    LocalDateTime now = LocalDateTime.now(clock);
    return repository.save(new Subscription(
        existing.id(),
        existing.projectId(),
        existing.productKey(),
        existing.consumerRef(),
        existing.consumptionMode(),
        SubscriptionStatus.CANCELLED,
        existing.createdBy(),
        existing.createdAt(),
        principal,
        now));
  }

  public List<Subscription> list(ProductKey productKey, ConsumerRef consumerRef) {
    return repository.list(currentProject.requireProjectId(), productKey, consumerRef);
  }

  private void requireDiscoverable(ProductKey productKey) {
    ProductLookupResult lookup = productDiscovery.get(productKey);
    if (lookup.state() == ProductLookupState.FOUND) {
      return;
    }
    String suffix = lookup.reason() == null || lookup.reason().isBlank()
        ? ""
        : ": " + lookup.reason();
    throw switch (lookup.state()) {
      case NOT_FOUND -> new IllegalArgumentException("Product not found: " + productKey);
      case NOT_DISCOVERABLE -> new IllegalArgumentException("Product is not discoverable: " + productKey);
      case FORBIDDEN -> new IllegalStateException("Product discovery forbidden" + suffix);
      case UNAVAILABLE -> new IllegalStateException("Product provider unavailable" + suffix);
      case FOUND -> new IllegalStateException("unreachable");
    };
  }

  private static String requireActor(String actor) {
    if (actor == null || actor.isBlank()) {
      throw new IllegalArgumentException("authenticated actor is required");
    }
    return actor.trim();
  }
}
