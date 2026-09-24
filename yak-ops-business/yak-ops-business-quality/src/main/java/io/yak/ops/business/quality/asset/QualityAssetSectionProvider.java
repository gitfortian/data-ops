package io.yak.ops.business.quality.asset;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

/** Quality-owned Asset Detail read adapter; all underlying facts remain Quality-owned. */
@Component
@ConditionalOnQualityEnabled
public class QualityAssetSectionProvider implements SectionProvider {

  @Override
  public java.util.Set<String> supportedSourceTypes() {
    return java.util.Set.of("METADATA");
  }

  private final QualityAssetSectionSummaryReader summaryReader;

  public QualityAssetSectionProvider(QualityAssetSectionSummaryReader summaryReader) {
    this.summaryReader = summaryReader;
  }

  @Override
  public SectionType sectionType() {
    return SectionType.QUALITY;
  }

  @Override
  public boolean supports(SectionContext context) {
    return "METADATA".equals(context.sourceType())
        && isCompleteCoordinate(context.attributes());
  }

  @Override
  public SectionContract query(SectionContext context) {
    long dataSourceId = Long.parseLong(context.attributes().get("dataSourceId"));
    QualityAssetSectionSummary summary = summaryReader.read(
        dataSourceId,
        blankToNull(context.attributes().get("databaseName")),
        blankToNull(context.attributes().get("schemaName")),
        context.attributes().get("tableName"));
    SectionStatus status = summary.monitorCount() > 0 ? SectionStatus.OK : SectionStatus.EMPTY;
    String reason = status == SectionStatus.EMPTY
        ? (summary.registered() ? "该物理表已纳入质量管理，尚未配置监控" : "该物理表尚未纳入质量管理")
        : null;
    Instant observedAt = Instant.now();
    String reference = summary.latestExecution() == null
        ? context.assetKey() : summary.latestExecution().executionNo();
    return new QualityAssetSectionResult(
        status,
        summary,
        reason,
        summary.latestExecution() == null || summary.latestExecution().finishedAt() == null ? null
            : summary.latestExecution().finishedAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
        List.of(new SectionAction("查看质量监控",
                summary.monitorId() == null ? "/data-quality/table-config"
                    : "/data-quality/monitor/" + summary.monitorId(),
                summary.monitorId() == null ? context.assetKey() : String.valueOf(summary.monitorId())),
            new SectionAction("查看质量运行记录",
                summary.latestExecution() == null ? "/data-quality/execution"
                    : "/data-quality/execution/" + summary.latestExecution().executionNo(), reference)),
        summary.latestExecution() == null ? List.of()
            : List.of(new SectionEvidence("QUALITY", summary.latestExecution().executionNo(), observedAt)),
        new SectionProvenance("QUALITY", reference, observedAt),
        new SectionCapability(true, true, null));
  }

  private static boolean isCompleteCoordinate(Map<String, String> attributes) {
    return StringUtils.hasText(attributes.get("dataSourceId"))
        && StringUtils.hasText(attributes.get("databaseName"))
        && StringUtils.hasText(attributes.get("tableName"));
  }

  private static String blankToNull(String value) {
    return StringUtils.hasText(value) ? value : null;
  }
}
