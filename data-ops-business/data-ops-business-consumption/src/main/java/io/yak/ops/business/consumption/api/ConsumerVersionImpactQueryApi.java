package io.yak.ops.business.consumption.api;

import io.yak.ops.spi.section.SectionStatus;
import java.time.LocalDateTime;
import java.util.List;

/** Authorized persisted windows only; no reconciliation, data query or business command. */
public interface ConsumerVersionImpactQueryApi {
  int WINDOW_LIMIT = 10;
  Result read(String productType, String productIdentity, String sourceVersionIdentity);
  record Consumer(String consumerType, String sourceDomain, String sourceIdentity, int recordCount,
      LocalDateTime lastObservedAt) {}
  record Window(SectionStatus status, int recordCount, String windowState, List<Consumer> consumers) {
    public Window { consumers = consumers == null ? List.of() : List.copyOf(consumers); }
  }
  record Result(String productType, String productIdentity, String sourceVersionIdentity, SectionStatus status,
      String membershipBasis, Window subscriptions, Window usage) {}
}
