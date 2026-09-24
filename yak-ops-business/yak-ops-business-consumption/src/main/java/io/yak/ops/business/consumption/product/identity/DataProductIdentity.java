package io.yak.ops.business.consumption.product.identity;

import io.yak.ops.business.consumption.product.domain.DataProductDomain;

/**
 * Stable identity of a consumable data product.
 */
public record DataProductIdentity(
        DataProductDomain productDomain,
        String sourceDomain,
        String sourceId
) {
}
