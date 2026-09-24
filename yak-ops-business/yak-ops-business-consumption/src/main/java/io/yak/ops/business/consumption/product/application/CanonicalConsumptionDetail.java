package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;

/** One canonical detail envelope preserving lookup semantics and stable backlinks. */
public record CanonicalConsumptionDetail(
    ProductLookupState state,
    DataProductView product,
    ConsumptionNavigation navigation,
    String reason) {}
