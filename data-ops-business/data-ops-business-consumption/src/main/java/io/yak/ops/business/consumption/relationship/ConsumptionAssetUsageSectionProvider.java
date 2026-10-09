package io.yak.ops.business.consumption.relationship;

import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.consumption.product.identity.ProductKey;
import io.yak.ops.business.consumption.product.model.ProductType;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionCapability;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionEvidence;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionProvenance;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read-only persisted Consumption windows for the federated Asset Usage section. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class ConsumptionAssetUsageSectionProvider implements SectionProvider {
  private static final int EVIDENCE_LIMIT = 200;
  private final ConsumerUsageSummaryReader summaryReader;

  @Override
  public SectionType sectionType() { return SectionType.USAGE; }

  @Override
  public java.util.Set<String> supportedSourceTypes() { return java.util.Set.of("DATASET"); }

  @Override
  public boolean supports(SectionContext context) {
    return "DATASET".equals(context.sourceType()) && validIdentity(context.sourceId());
  }

  @Override
  public SectionContract query(SectionContext context) {
    ProductKey productKey = new ProductKey(ProductType.DATASET, context.sourceId());
    ConsumerUsageSummaryReader.Summary summary = summaryReader.read(productKey, EVIDENCE_LIMIT);
    SectionStatus status = summary.status();
    String reason = switch (status) {
      case EMPTY -> "当前持久化窗口没有有效订阅或归一化成功使用；未同步来源，不证明没有消费者";
      case UNAVAILABLE, PERMISSION_DENIED -> summary.coverageNote();
      default -> null;
    };
    Instant observedAt = summary.lastObservedAt() == null ? null
        : summary.lastObservedAt().atZone(ZoneId.systemDefault()).toInstant();
    String returnAssetId = context.attributes().get("returnAssetId");
    String encodedKey = URLEncoder.encode(productKey.value(), StandardCharsets.UTF_8);
    String target = "/data-analysis/consumption/" + encodedKey
        + (returnAssetId == null ? "" : "?returnAssetId=" + returnAssetId);
    return new AssetSectionResult(
        SectionType.USAGE, status, "CONSUMING_DOMAINS", summary, reason, null,
        List.of(new SectionAction("查看已知消费者", target, productKey.value())),
        status == SectionStatus.OK
            ? List.of(new SectionEvidence("CONSUMPTION", productKey.value(), observedAt)) : List.of(),
        new SectionProvenance("CONSUMPTION", productKey.value(), observedAt),
        new SectionCapability(true, status == SectionStatus.OK || status == SectionStatus.EMPTY, reason));
  }

  private static boolean validIdentity(String sourceId) {
    if (sourceId == null || sourceId.isBlank()) return false;
    try { return Long.parseLong(sourceId) > 0; }
    catch (NumberFormatException invalid) { return false; }
  }
}
