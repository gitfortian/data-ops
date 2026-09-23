package io.yak.ops.business.asset.quality;

import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Quality section read boundary.
 *
 * <p>Quality domain owns quality facts. Asset only exposes the product section and never
 * creates a second quality truth.</p>
 */
@Component
public class QualitySectionProvider implements SectionProvider {

  @Override
  public SectionType sectionType() {
    return SectionType.QUALITY;
  }

  @Override
  public boolean supports(SectionContext context) {
    return context != null && context.sourceType() != null;
  }

  @Override
  public SectionContract query(SectionContext context) {
    Instant now = Instant.now();
    // Runtime quality adapters plug into this boundary. Absence of an adapter is not a
    // quality failure, therefore it is reported as UNAVAILABLE.
    return new QualityContract(
        SectionStatus.UNAVAILABLE,
        "质量读侧尚未接入",
        now,
        new QualitySummary(Map.of(
            "assetKey", context.assetKey(),
            "latestStatus", "UNAVAILABLE",
            "summary", "等待 Quality Truth Owner 提供最近质量状态")),
        List.of(new SectionEvidence("QUALITY", context.assetKey(), now)),
        new SectionProvenance("QUALITY", context.assetKey(), now));
  }

  private record QualityContract(
      SectionStatus status,
      String reason,
      Instant updatedAt,
      SectionSummary summary,
      List<SectionEvidence> evidence,
      SectionProvenance provenance) implements SectionContract {

    @Override
    public String ownerDomain() {
      return "QUALITY";
    }

    @Override
    public List<SectionAction> actions() {
      return List.of(new SectionAction("查看质量规则", "quality", provenance.sourceId()));
    }

    @Override
    public SectionCapability capability() {
      return new SectionCapability(true, false, "Quality Truth Owner read adapter pending");
    }
  }

  private record QualitySummary(Map<String, Object> values) implements SectionSummary {}
}
