package io.yak.ops.business.consumption.product.model;

import io.yak.ops.business.consumption.product.identity.DomainRef;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.identity.SourceRef;
import io.yak.ops.business.consumption.product.identity.SourceVersionRef;
import java.util.List;
import java.util.Objects;

/** Rebuildable common shell for the canonical consumption detail. */
public record DataProductView(
    ProductKey productKey,
    SourceRef sourceRef,
    DomainRef producerRef,
    DomainRef assetRef,
    String name,
    String description,
    String owner,
    Long projectId,
    String visibility,
    SourceVersionRef activeVersion,
    SourceLifecycleState lifecycle,
    AvailabilityState availability,
    AccessProjection access,
    List<ProductSectionState> sections,
    ProductContractPayload contractPayload) {

  public DataProductView {
    Objects.requireNonNull(productKey, "productKey");
    Objects.requireNonNull(sourceRef, "sourceRef");
    name = requireText(name, "name");
    Objects.requireNonNull(projectId, "projectId");
    Objects.requireNonNull(lifecycle, "lifecycle");
    Objects.requireNonNull(availability, "availability");
    Objects.requireNonNull(access, "access");
    Objects.requireNonNull(contractPayload, "contractPayload");
    if (!productKey.equals(sourceRef.productKey())) {
      throw new IllegalArgumentException("productKey must be derived from sourceRef");
    }
    if (contractPayload.productType() != productKey.productType()) {
      throw new IllegalArgumentException("contract payload type must match productKey type");
    }
    description = normalizeOptional(description);
    owner = normalizeOptional(owner);
    visibility = normalizeOptional(visibility);
    sections = sections == null ? List.of() : List.copyOf(sections);
    requireExplicitMissingEvidence(owner, "ownership", sections);
    requireExplicitMissingEvidence(visibility, "visibility", sections);
  }

  private static void requireExplicitMissingEvidence(
      String value, String sectionKey, List<ProductSectionState> sections) {
    if (value != null) return;
    boolean explained = sections.stream().anyMatch(section ->
        sectionKey.equals(section.sectionKey()) && section.state() != ProviderEvidenceState.READY);
    if (!explained) {
      throw new IllegalArgumentException(sectionKey + " must be present or carry explicit non-READY evidence");
    }
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
