package io.yak.ops.business.asset.api;

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
import java.util.Map;

/** Read-only section response; facts stay with their declared owner domain. */
public record AssetSectionResult(
    SectionType sectionType,
    SectionStatus status,
    String ownerDomain,
    Payload summary,
    String reason,
    Instant updatedAt,
    List<SectionAction> actions,
    List<SectionEvidence> evidence,
    SectionProvenance provenance,
    SectionCapability capability) implements SectionContract {

  public record Payload(Map<String, Object> values) implements SectionSummary {}

  public AssetSectionResult {
    actions = actions == null ? List.of() : List.copyOf(actions);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }
}
