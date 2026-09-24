package io.yak.ops.business.consumption.product.application;

import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** One canonical detail envelope preserving lookup semantics, evidence and stable backlinks. */
public record CanonicalConsumptionDetail(
    ProductLookupState state,
    DataProductView product,
    ConsumptionNavigation navigation,
    List<GovernanceEvidence> governanceEvidence,
    String reason) {

  public CanonicalConsumptionDetail {
    governanceEvidence = governanceEvidence == null ? List.of() : List.copyOf(governanceEvidence);
  }

  public record GovernanceEvidence(
      String sectionKey,
      ProviderEvidenceState state,
      String ownerDomain,
      Instant observedAt,
      Map<String, Object> facts,
      String reason) {
    public GovernanceEvidence {
      facts = facts == null ? Map.of() : Map.copyOf(facts);
    }
  }
}
