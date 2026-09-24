package io.yak.ops.business.consumption.product.provider;

import io.yak.ops.business.consumption.product.model.AvailabilityState;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.consumption.product.model.SourceLifecycleState;

/**
 * Provider-local discovery criteria.
 *
 * Project membership and visibility authorization are always enforced server-side even when these
 * fields are also used as explicit filters.
 */
public record ProductSearchCriteria(
    ProductType productType,
    String keyword,
    String owner,
    Long projectId,
    String visibility,
    SourceLifecycleState lifecycle,
    AvailabilityState availability) {
}
