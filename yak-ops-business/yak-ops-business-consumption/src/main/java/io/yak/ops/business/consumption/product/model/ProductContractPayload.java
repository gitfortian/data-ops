package io.yak.ops.business.consumption.product.model;

/** Marker for type-specific Dataset/Data Service contract payloads. */
public interface ProductContractPayload {
  ProductType productType();
}
