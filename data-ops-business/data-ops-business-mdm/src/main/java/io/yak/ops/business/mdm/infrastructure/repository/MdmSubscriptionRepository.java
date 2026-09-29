package io.yak.ops.business.mdm.infrastructure.repository;

import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
import java.util.List;
import java.util.Optional;

/** Project-scoped persistence boundary for the master data subscription (ticket 59). */
public interface MdmSubscriptionRepository {

  MdmSubscription insert(MdmSubscription subscription, String operator);

  Optional<MdmSubscription> findById(Long id);

  List<MdmSubscription> listByEntity(Long entityId);

  List<MdmSubscription> listActiveByEntity(Long entityId);

  boolean existsBySubscriber(Long entityId, String subscriberCode, Long excludeId);

  boolean update(MdmSubscription subscription);

  boolean deleteById(Long id);

  long countActiveByEntity(Long entityId);
}
