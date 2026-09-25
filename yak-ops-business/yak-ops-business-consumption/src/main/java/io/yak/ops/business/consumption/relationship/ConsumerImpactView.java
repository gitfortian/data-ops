package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.consumption.product.identity.ProductKey;
import java.time.LocalDateTime;
import java.util.List;

/** Known consumer impact only. It never claims to enumerate all external consumers. */
public record ConsumerImpactView(
    ProductKey productKey,
    EvidenceState subscriptionState,
    EvidenceState usageState,
    List<KnownConsumer> consumers,
    String coverageNote) {

  public ConsumerImpactView {
    consumers = consumers == null ? List.of() : List.copyOf(consumers);
  }

  public enum EvidenceState {
    READY,
    EMPTY,
    UNAVAILABLE,
    FORBIDDEN
  }

  public record KnownConsumer(
      ConsumerRef consumerRef,
      List<ConsumptionMode> declaredModes,
      List<ConsumptionMode> observedModes,
      int activeSubscriptionCount,
      int successfulUsageCount,
      LocalDateTime lastDeclaredAt,
      LocalDateTime lastObservedAt,
      List<String> providerEvidenceRefs) {

    public KnownConsumer {
      declaredModes = declaredModes == null ? List.of() : List.copyOf(declaredModes);
      observedModes = observedModes == null ? List.of() : List.copyOf(observedModes);
      providerEvidenceRefs = providerEvidenceRefs == null ? List.of() : List.copyOf(providerEvidenceRefs);
    }
  }
}
