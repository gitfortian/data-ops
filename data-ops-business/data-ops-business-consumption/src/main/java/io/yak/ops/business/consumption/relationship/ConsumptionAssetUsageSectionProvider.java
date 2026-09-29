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
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Supplies Consumption-owned Dataset consumer facts to the federated Asset Usage section. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class ConsumptionAssetUsageSectionProvider implements SectionProvider {

  private static final int EVIDENCE_LIMIT = 200;

  private final ConsumerImpactService consumerImpactService;

  public record DatasetUsageSummary(
      String scope,
      int consumerCount,
      int userCount,
      int teamCount,
      int dashboardCount,
      int dataServiceCount,
      int jobCount,
      int successfulUsageCount,
      int activeSubscriptionCount,
      LocalDateTime lastObservedAt,
      String coverageNote) implements SectionSummary {

    @Override
    public Map<String, Object> values() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("scope", scope);
      values.put("consumerCount", consumerCount);
      values.put("userCount", userCount);
      values.put("teamCount", teamCount);
      values.put("dashboardCount", dashboardCount);
      values.put("dataServiceCount", dataServiceCount);
      values.put("jobCount", jobCount);
      values.put("successfulUsageCount", successfulUsageCount);
      values.put("activeSubscriptionCount", activeSubscriptionCount);
      values.put("lastObservedAt", lastObservedAt);
      values.put("coverageNote", coverageNote);
      return values;
    }
  }

  @Override
  public SectionType sectionType() {
    return SectionType.USAGE;
  }

  @Override
  public java.util.Set<String> supportedSourceTypes() {
    return java.util.Set.of("DATASET");
  }

  @Override
  public boolean supports(SectionContext context) {
    return "DATASET".equals(context.sourceType()) && validIdentity(context.sourceId());
  }

  @Override
  public SectionContract query(SectionContext context) {
    ProductKey productKey = new ProductKey(ProductType.DATASET, context.sourceId());
    ConsumerImpactView impact = consumerImpactService.view(productKey, EVIDENCE_LIMIT);
    List<ConsumerImpactView.KnownConsumer> consumers = impact.consumers();
    SectionStatus status = status(impact, consumers);
    DatasetUsageSummary summary = summarize(impact, consumers);
    String reason = switch (status) {
      case EMPTY -> "当前没有声明订阅或已归一化的成功使用证据";
      case UNAVAILABLE -> impact.coverageNote();
      default -> null;
    };
    Instant observedAt = summary.lastObservedAt() == null
        ? Instant.now()
        : summary.lastObservedAt().atZone(ZoneId.systemDefault()).toInstant();
    String returnAssetId = context.attributes().get("returnAssetId");
    String encodedKey = URLEncoder.encode(productKey.value(), StandardCharsets.UTF_8);
    String target = "/data-analysis/consumption/" + encodedKey
        + (returnAssetId == null ? "" : "?returnAssetId=" + returnAssetId);
    return new AssetSectionResult(
        SectionType.USAGE,
        status,
        "CONSUMING_DOMAINS",
        summary,
        reason,
        null,
        List.of(new SectionAction("查看已知消费者", target, productKey.value())),
        status == SectionStatus.EMPTY ? List.of()
            : List.of(new SectionEvidence("CONSUMPTION", productKey.value(), observedAt)),
        new SectionProvenance("CONSUMPTION", productKey.value(), observedAt),
        new SectionCapability(true, status != SectionStatus.UNAVAILABLE, reason));
  }

  private static DatasetUsageSummary summarize(
      ConsumerImpactView impact, List<ConsumerImpactView.KnownConsumer> consumers) {
    int successfulUsageCount = consumers.stream()
        .mapToInt(ConsumerImpactView.KnownConsumer::successfulUsageCount)
        .sum();
    int activeSubscriptionCount = consumers.stream()
        .mapToInt(ConsumerImpactView.KnownConsumer::activeSubscriptionCount)
        .sum();
    LocalDateTime lastObservedAt = consumers.stream()
        .map(ConsumerImpactView.KnownConsumer::lastObservedAt)
        .filter(java.util.Objects::nonNull)
        .max(LocalDateTime::compareTo)
        .orElse(null);
    return new DatasetUsageSummary(
        "Dataset 已声明消费者与归一化成功查询证据",
        consumers.size(),
        count(consumers, ConsumerType.USER),
        count(consumers, ConsumerType.TEAM),
        count(consumers, ConsumerType.DASHBOARD),
        count(consumers, ConsumerType.DATA_SERVICE),
        count(consumers, ConsumerType.JOB),
        successfulUsageCount,
        activeSubscriptionCount,
        lastObservedAt,
        impact.coverageNote());
  }

  private static SectionStatus status(
      ConsumerImpactView impact, List<ConsumerImpactView.KnownConsumer> consumers) {
    if (!consumers.isEmpty()) {
      return SectionStatus.OK;
    }
    if (impact.subscriptionState() == ConsumerImpactView.EvidenceState.UNAVAILABLE
        || impact.usageState() == ConsumerImpactView.EvidenceState.UNAVAILABLE) {
      return SectionStatus.UNAVAILABLE;
    }
    return SectionStatus.EMPTY;
  }

  private static int count(
      List<ConsumerImpactView.KnownConsumer> consumers, ConsumerType type) {
    return (int) consumers.stream()
        .filter(consumer -> consumer.consumerRef().consumerType() == type)
        .count();
  }

  private static boolean validIdentity(String sourceId) {
    if (sourceId == null || sourceId.isBlank()) {
      return false;
    }
    try {
      return Long.parseLong(sourceId) > 0;
    } catch (NumberFormatException invalid) {
      return false;
    }
  }
}
