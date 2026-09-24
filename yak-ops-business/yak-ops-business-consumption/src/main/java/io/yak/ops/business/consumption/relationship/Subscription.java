package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import java.time.LocalDateTime;
import java.util.Objects;

/** Consumption-owned declared dependency. Subscription never grants product access. */
public record Subscription(
    Long id,
    Long projectId,
    ProductKey productKey,
    ConsumerRef consumerRef,
    ConsumptionMode consumptionMode,
    SubscriptionStatus status,
    String createdBy,
    LocalDateTime createdAt,
    String updatedBy,
    LocalDateTime updatedAt) {

  public Subscription {
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(productKey, "productKey");
    Objects.requireNonNull(consumerRef, "consumerRef");
    Objects.requireNonNull(consumptionMode, "consumptionMode");
    Objects.requireNonNull(status, "status");
    createdBy = requireText(createdBy, "createdBy");
    Objects.requireNonNull(createdAt, "createdAt");
    updatedBy = requireText(updatedBy, "updatedBy");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
