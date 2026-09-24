package io.yak.ops.business.metric.usage;

import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.metric.api.MetricUsageApi;
import io.yak.ops.business.metric.config.ConditionalOnMetricPersistence;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionMapSummary;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads Metric-owned consumer reference counts for the federated Asset Usage section. */
@Component
@ConditionalOnMetricPersistence
@RequiredArgsConstructor
public class MetricAssetUsageSectionProvider implements SectionProvider {

  private final MetricUsageApi metricUsageApi;

  @Override
  public SectionType sectionType() {
    return SectionType.USAGE;
  }

  @Override
  public boolean supports(SectionContext context) {
    return "METRIC".equals(context.sourceType()) && parseMetricId(context.sourceId()) != null;
  }

  @Override
  public SectionContract query(SectionContext context) {
    Long metricId = parseMetricId(context.sourceId());
    if (metricId == null) {
      return result(context, SectionStatus.UNAVAILABLE, Map.of(), "指标身份无法解析");
    }
    MetricUsageApi.UsageSummary summary = metricUsageApi.summary(metricId);
    SectionStatus status = summary.totalCount() > 0 ? SectionStatus.OK : SectionStatus.EMPTY;
    Map<String, Object> values = Map.of(
        "metricId", summary.metricId(),
        "scope", "已记录的指标引用总量（非实时 API 调用次数）",
        "totalCount", summary.totalCount(),
        "reportCount", summary.reportCount(),
        "datasetCount", summary.datasetCount(),
        "dashboardCount", summary.dashboardCount(),
        "apiCount", summary.apiCount(),
        "screenCount", summary.screenCount());
    return result(context, status, values,
        status == SectionStatus.EMPTY ? "当前没有已记录的指标引用" : null);
  }

  private static SectionContract result(
      SectionContext context, SectionStatus status, Map<String, Object> values, String reason) {
    Instant observedAt = Instant.now();
    String assetId = context.attributes().get("returnAssetId");
    List<SectionAction> actions = assetId == null ? List.of() : List.of(
        new SectionAction("查看指标使用详情",
            "/metric/manage/" + context.sourceId() + "?returnAssetId=" + assetId,
            context.sourceId()));
    return new AssetSectionResult(
        SectionType.USAGE, status, "METRIC", new SectionMapSummary(values), reason,
        null, actions, status == SectionStatus.EMPTY ? List.of()
            : List.of(new SectionEvidence("METRIC", context.sourceId(), observedAt)),
        new SectionProvenance("METRIC", context.sourceId(), observedAt),
        new SectionCapability(true, status != SectionStatus.UNAVAILABLE, reason));
  }

  private static Long parseMetricId(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
