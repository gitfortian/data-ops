package io.yak.ops.business.consumption.product.discovery;

import io.yak.ops.business.consumption.product.model.DataProductView;
import io.yak.ops.business.consumption.product.model.ProviderEvidenceState;
import io.yak.ops.business.consumption.product.provider.ProductLookupState;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Canonical detail keeps source truth, navigation and governance evidence in one read envelope. */
public record CanonicalProductDetail(
    ProductLookupState state,
    DataProductView product,
    ProductNavigation navigation,
    List<GovernanceEvidence> governanceEvidence,
    String reason) {

  public CanonicalProductDetail {
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
