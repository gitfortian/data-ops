package io.yak.ops.business.security.asset;

import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Security-owned, read-only view of classification evidence for Asset Detail. */
record SecuritySectionContract(
    SectionStatus status,
    Map<String, Object> values,
    String reason,
    List<SectionAction> actions,
    List<SectionEvidence> evidence,
    SectionProvenance provenance,
    Instant observedAt,
    SectionCapability capability) implements SectionContract {

  @Override public SectionType sectionType() { return SectionType.SECURITY; }
  @Override public String ownerDomain() { return "SECURITY"; }
  @Override public SectionSummary summary() { return new SectionMapSummary(values); }
  @Override public Instant updatedAt() { return null; }
}
