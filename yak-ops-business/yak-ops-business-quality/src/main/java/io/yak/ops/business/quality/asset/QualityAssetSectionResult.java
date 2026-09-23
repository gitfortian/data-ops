package io.yak.ops.business.quality.asset;

import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;

/** Quality-owned Asset Section contract; it contains only a read projection. */
public record QualityAssetSectionResult(
    SectionStatus status,
    SectionSummary summary,
    String reason,
    Instant updatedAt,
    List<SectionAction> actions,
    List<SectionEvidence> evidence,
    SectionProvenance provenance,
    SectionCapability capability) implements SectionContract {

  public QualityAssetSectionResult {
    actions = List.copyOf(actions);
    evidence = List.copyOf(evidence);
  }

  @Override public SectionType sectionType() { return SectionType.QUALITY; }
  @Override public String ownerDomain() { return "QUALITY"; }
}
