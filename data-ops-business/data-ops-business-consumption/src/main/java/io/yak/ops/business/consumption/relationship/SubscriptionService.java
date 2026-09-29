package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.discovery.ProductDiscoveryService;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.provider.ProductLookupResult;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.dataservice.access.DataServiceConsumerManager;
import io.yak.ops.common.constant.dataservice.DataServicePermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.core.security.ActionAccessDeniedException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns declared consumer dependency truth. Subscription never changes source access policy. */
@Service
@ConditionalOnDataSourceEnabled
public class SubscriptionService {

  private final SubscriptionRepository repository;
  private final ProductDiscoveryService productDiscovery;
  private final CurrentProject currentProject;
  private final DataServiceConsumerManager dataServiceConsumers;
  private final ActionAuthorization actionAuthorization;
  private final Clock clock = Clock.systemDefaultZone();

  @Autowired
  public SubscriptionService(
      SubscriptionRepository repository,
      ProductDiscoveryService productDiscovery,
      CurrentProject currentProject,
      DataServiceConsumerManager dataServiceConsumers,
      ActionAuthorization actionAuthorization) {
    this.repository = repository;
    this.productDiscovery = productDiscovery;
    this.currentProject = currentProject;
    this.dataServiceConsumers = dataServiceConsumers;
    this.actionAuthorization = actionAuthorization;
  }

  /** Compatibility constructor for self-user subscription unit tests. */
  public SubscriptionService(
      SubscriptionRepository repository,
      ProductDiscoveryService productDiscovery,
      CurrentProject currentProject) {
    this(repository, productDiscovery, currentProject, null, null);
  }

  @Transactional
  public Subscription subscribe(
      ProductKey productKey,
      ConsumerRef consumerRef,
      ConsumptionMode consumptionMode,
      String actor) {
    Long projectId = currentProject.requireProjectId();
    String principal = requireActor(actor);
    authorizeConsumer(consumerRef, principal);
    requireDiscoverable(productKey);

    Subscription existing = repository.find(projectId, productKey, consumerRef, consumptionMode)
        .orElse(null);
    if (existing != null && existing.status() == SubscriptionStatus.ACTIVE) {
      return existing;
    }
    if (existing != null && existing.status() == SubscriptionStatus.REVOKED) {
      throw new IllegalStateException("A revoked subscription cannot be reactivated");
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
    authorizeConsumer(existing.consumerRef(), principal);
    if (existing.status() == SubscriptionStatus.REVOKED) return existing;
    return saveTransition(existing, principal, SubscriptionStatus.REVOKED);
  }

  @Transactional
  public Subscription suspend(Long subscriptionId, String actor) {
    return transition(subscriptionId, actor, SubscriptionStatus.SUSPENDED);
  }

  @Transactional
  public Subscription resume(Long subscriptionId, String actor) {
    return transition(subscriptionId, actor, SubscriptionStatus.ACTIVE);
  }

  private Subscription transition(Long subscriptionId, String actor, SubscriptionStatus target) {
    Long projectId = currentProject.requireProjectId();
    String principal = requireActor(actor);
    Subscription existing = repository.findById(projectId, subscriptionId)
        .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
    authorizeConsumer(existing.consumerRef(), principal);
    if (existing.status() == target) return existing;
    if (existing.status() == SubscriptionStatus.REVOKED) {
      throw new IllegalStateException("A revoked subscription cannot be changed");
    }
    if (target == SubscriptionStatus.SUSPENDED && existing.status() != SubscriptionStatus.ACTIVE) {
      throw new IllegalStateException("Only an active subscription can be suspended");
    }
    if (target == SubscriptionStatus.ACTIVE && existing.status() != SubscriptionStatus.SUSPENDED) {
      throw new IllegalStateException("Only a suspended subscription can be resumed");
    }
    return saveTransition(existing, principal, target);
  }

  private Subscription saveTransition(
      Subscription existing, String principal, SubscriptionStatus target) {
    LocalDateTime now = LocalDateTime.now(clock);
    return repository.save(new Subscription(
        existing.id(),
        existing.projectId(),
        existing.productKey(),
        existing.consumerRef(),
        existing.consumptionMode(),
        target,
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

  private static void requireOwnConsumer(ConsumerRef consumerRef, String actor) {
    if (consumerRef.consumerType() == ConsumerType.USER
        && "SECURITY_PRINCIPAL".equals(consumerRef.sourceDomain())
        && actor.equals(consumerRef.sourceIdentity())) {
      return;
    }
    throw new ActionAccessDeniedException("consumption:subscription:own-consumer");
  }

  private void authorizeConsumer(ConsumerRef consumerRef, String actor) {
    if (consumerRef.consumerType() == ConsumerType.USER) {
      requireOwnConsumer(consumerRef, actor);
      return;
    }
    if (consumerRef.consumerType() == ConsumerType.DATA_SERVICE
        && "DATA_SERVICE_CONSUMER".equals(consumerRef.sourceDomain())) {
      if (dataServiceConsumers == null || actionAuthorization == null) {
        throw new ActionAccessDeniedException(DataServicePermissionCode.ACCESS);
      }
      actionAuthorization.requirePermission(DataServicePermissionCode.ACCESS);
      Long consumerId = parsePositiveId(consumerRef.sourceIdentity());
      if (consumerId == null) throw new IllegalArgumentException("Invalid Data Service Consumer identity");
      dataServiceConsumers.get(consumerId); // Owning reader enforces current Project Space.
      return;
    }
    throw new ActionAccessDeniedException("consumption:subscription:consumer-owner");
  }

  private static Long parsePositiveId(String value) {
    try {
      long id = Long.parseLong(value);
      return id > 0 ? id : null;
    } catch (RuntimeException invalid) {
      return null;
    }
  }
}
