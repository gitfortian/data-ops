package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository {

  Optional<Subscription> find(
      Long projectId,
      ProductKey productKey,
      ConsumerRef consumerRef,
      ConsumptionMode consumptionMode);

  Optional<Subscription> findById(Long projectId, Long subscriptionId);

  Subscription save(Subscription subscription);

  List<Subscription> list(Long projectId, ProductKey productKey, ConsumerRef consumerRef);

  /** Most recently updated ACTIVE rows, ordered by updatedAt/id descending, at most 200. */
  List<Subscription> listRecentActive(Long projectId, ProductKey productKey, int limit);
}
