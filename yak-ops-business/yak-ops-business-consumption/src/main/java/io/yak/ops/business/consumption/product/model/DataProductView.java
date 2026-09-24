package io.yak.ops.business.consumption.product.model;

import io.yak.ops.business.consumption.product.domain.DataProductDomain;

/**
 * Common projection view exposed by consumption domain.
 */
public record DataProductView(
        DataProductDomain domain,
        String productId,
        String name,
        String owner
) {
}
