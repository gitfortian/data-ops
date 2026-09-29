package io.yak.ops.business.security.asset;

import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.security.config.ConditionalOnSecurityPersistence;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Security-owned read adapter; Asset supplies only stable source coordinates and return context. */
@Component
@ConditionalOnSecurityPersistence
@RequiredArgsConstructor
public class SecurityAssetSectionProvider implements SectionProvider {

  public record ClassificationFact(
      String objectKey, String levelCode, String levelName, Integer levelRank,
      String categoryCode, String categoryName, String status) {
    static ClassificationFact from(ClassificationView view) {
      return new ClassificationFact(view.objectKey(), view.levelCode(), view.levelName(),
          view.levelRank(), view.categoryCode(), view.categoryName(), view.status());
    }
  }

  public record SecurityClassificationSummary(
      int classificationCount,
      List<ClassificationFact> classifications,
      String strategySummaryStatus,
      String strategySummaryReason) implements SectionSummary {
    @Override public Map<String, Object> values() {
      return Map.of(
          "classificationCount", classificationCount,
          "classifications", classifications,
          "strategySummaryStatus", strategySummaryStatus,
          "strategySummaryReason", strategySummaryReason);
    }
  }

  @Override
  public java.util.Set<String> supportedSourceTypes() {
    return java.util.Set.of("METADATA", "MODEL", "METRIC", "DATASET", "DASHBOARD", "CHART", "TASK");
  }

  private final SecurityClassificationQueryApi classificationQuery;

  @Override
  public SectionType sectionType() {
    return SectionType.SECURITY;
  }

  @Override
  public boolean supports(SectionContext context) {
    return context.sourceId() != null && !context.sourceId().isBlank();
  }

  @Override
  public SectionContract query(SectionContext context) {
    Instant observedAt = Instant.now();
    if ("METADATA".equals(context.sourceType())
        && (!StringUtils.hasText(context.attributes().get("databaseName"))
            || !StringUtils.hasText(context.attributes().get("dataSourceId"))
            || !StringUtils.hasText(context.attributes().get("tableName")))) {
      return response(context, SectionStatus.UNAVAILABLE, summary(List.of()),
          "Metadata 未提供完整数据库与表坐标，无法准确匹配 Security 对象", observedAt);
    }
    List<ClassificationView> classifications = classifications(context);
    if (classifications.isEmpty()) {
      return response(context, SectionStatus.EMPTY, summary(List.of()),
          "Security 域没有返回该对象的分级记录", observedAt);
    }
    return response(context, SectionStatus.OK,
        summary(classifications.stream().map(ClassificationFact::from).toList()), null, observedAt);
  }

  private static SecurityClassificationSummary summary(List<ClassificationFact> facts) {
    return new SecurityClassificationSummary(facts.size(), facts, "UNAVAILABLE",
        "当前 Security 稳定读接口不提供对象级访问策略与脱敏匹配摘要");
  }

  private List<ClassificationView> classifications(SectionContext context) {
    String database = context.attributes().get("databaseName");
    String datasourceId = context.attributes().get("dataSourceId");
    String table = context.attributes().get("tableName");
    if (StringUtils.hasText(datasourceId) && StringUtils.hasText(database)
        && StringUtils.hasText(table)) {
      return classificationQuery.findActiveByTable(datasourceId, database, table);
    }
    ClassificationView exact = classificationQuery.find(context.assetKey());
    return exact == null || !"ACTIVE".equals(exact.status()) ? List.of() : List.of(exact);
  }

  private static SectionContract response(
      SectionContext context, SectionStatus status, SecurityClassificationSummary summary,
      String reason, Instant observedAt) {
    String sourceId = context.assetKey();
    String returnAssetId = context.attributes().get("returnAssetId");
    String target = "/data-security/classification?activeTab=classification";
    String database = context.attributes().get("databaseName");
    String table = context.attributes().get("tableName");
    if (StringUtils.hasText(database) && StringUtils.hasText(table)) {
      target += "&keyword=" + java.net.URLEncoder.encode(
          database + "." + table, java.nio.charset.StandardCharsets.UTF_8);
    }
    if (returnAssetId != null && returnAssetId.matches("\\d+")) {
      target += "&returnAssetId=" + returnAssetId;
    }
    List<SectionAction> actions = List.of(
        new SectionAction("打开安全分级分类", target, sourceId));
    return new io.yak.ops.business.security.asset.SecuritySectionContract(
        status, summary, reason, actions,
        status == SectionStatus.EMPTY ? List.of()
            : List.of(new SectionEvidence("SECURITY", sourceId, observedAt)),
        new SectionProvenance("SECURITY", sourceId, observedAt), observedAt,
        new SectionCapability(true, status != SectionStatus.UNAVAILABLE, reason));
  }
}
