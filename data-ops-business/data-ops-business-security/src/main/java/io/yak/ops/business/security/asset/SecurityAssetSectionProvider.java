package io.yak.ops.business.security.asset;

import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.security.application.AccessPolicyService;
import io.yak.ops.business.security.application.MaskingService;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
      String categoryCode, String categoryName, String status, Boolean maskingConfigured) {
    static ClassificationFact from(ClassificationView view, Boolean maskingConfigured) {
      return new ClassificationFact(view.objectKey(), view.levelCode(), view.levelName(),
          view.levelRank(), view.categoryCode(), view.categoryName(), view.status(), maskingConfigured);
    }
  }

  public record SecurityClassificationSummary(
      int classificationCount,
      List<ClassificationFact> classifications,
      Long applicableReadPolicyCount,
      String accessPolicySummaryStatus,
      String accessPolicySummaryReason,
      Integer maskingConfiguredCount,
      Integer unmaskedClassifiedCount,
      String maskingSummaryStatus,
      String maskingSummaryReason,
      String complianceSummaryStatus,
      String complianceSummaryReason,
      String strategySummaryStatus,
      String strategySummaryReason) implements SectionSummary {
    @Override public Map<String, Object> values() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("classificationCount", classificationCount);
      result.put("classifications", classifications);
      result.put("applicableReadPolicyCount", applicableReadPolicyCount);
      result.put("accessPolicySummaryStatus", accessPolicySummaryStatus);
      result.put("accessPolicySummaryReason", accessPolicySummaryReason);
      result.put("maskingConfiguredCount", maskingConfiguredCount);
      result.put("unmaskedClassifiedCount", unmaskedClassifiedCount);
      result.put("maskingSummaryStatus", maskingSummaryStatus);
      result.put("maskingSummaryReason", maskingSummaryReason);
      result.put("complianceSummaryStatus", complianceSummaryStatus);
      result.put("complianceSummaryReason", complianceSummaryReason);
      result.put("strategySummaryStatus", strategySummaryStatus);
      result.put("strategySummaryReason", strategySummaryReason);
      return result;
    }
  }

  @Override
  public java.util.Set<String> supportedSourceTypes() {
    return java.util.Set.of("METADATA", "MODEL", "METRIC", "DATASET", "DASHBOARD", "CHART", "TASK");
  }

  private final SecurityClassificationQueryApi classificationQuery;
  private final AccessPolicyService accessPolicyService;
  private final MaskingService maskingService;

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
      return response(context, SectionStatus.UNAVAILABLE, summary(context, List.of()),
          "Metadata 未提供完整数据库与表坐标，无法准确匹配 Security 对象", observedAt);
    }
    List<ClassificationView> classifications = classifications(context);
    SecurityClassificationSummary summary = summary(context, classifications);
    if (classifications.isEmpty()) {
      if (summary.applicableReadPolicyCount() != null && summary.applicableReadPolicyCount() > 0) {
        return response(context, SectionStatus.OK, summary,
            "没有分级记录；仍展示匹配的已审批 READ 规则配置摘要", observedAt);
      }
      return response(context, SectionStatus.EMPTY, summary,
          "Security 域没有返回该对象的分级记录", observedAt);
    }
    return response(context, SectionStatus.OK, summary, null, observedAt);
  }

  private SecurityClassificationSummary summary(
      SectionContext context, List<ClassificationView> classifications) {
    Map<String, Boolean> maskingCoverage = Map.of();
    String maskingStatus = classifications.isEmpty() ? "NOT_APPLICABLE" : "OK";
    String maskingReason = null;
    try {
      maskingCoverage = maskingService.maskingCoverage(classifications);
    } catch (RuntimeException ex) {
      maskingStatus = "UNAVAILABLE";
      maskingReason = "无法读取当前项目的脱敏策略与算法配置";
    }
    Map<String, Boolean> resolvedMaskingCoverage = maskingCoverage;
    int maskedCount = (int) classifications.stream()
        .filter(view -> Boolean.TRUE.equals(resolvedMaskingCoverage.get(view.objectKey())))
        .count();
    int unmaskedCount = Math.max(0, classifications.size() - maskedCount);
    if (classifications.isEmpty()) {
      maskingReason = "该对象没有活动分级记录，无需计算分级对象的脱敏策略匹配情况";
    } else if ("OK".equals(maskingStatus) && unmaskedCount > 0) {
      maskingReason = "部分活动分级对象没有匹配到可用脱敏策略与算法";
    }

    Long datasourceId = positiveLong(context.attributes().get("dataSourceId"));
    String database = context.attributes().get("databaseName");
    String table = context.attributes().get("tableName");
    boolean hasTableCoordinates = datasourceId != null
        && StringUtils.hasText(database) && StringUtils.hasText(table);
    Long applicableReadPolicyCount = null;
    String accessStatus;
    String accessReason;
    if (!hasTableCoordinates) {
      accessStatus = "UNAVAILABLE";
      accessReason = "缺少完整数据源、数据库和表坐标，无法准确匹配对象级 READ 规则";
    } else {
      try {
        Collection<Long> levelIds = classifications.stream()
            .map(ClassificationView::levelId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        applicableReadPolicyCount = accessPolicyService.countApplicableReadPolicies(
            datasourceId, database, table, levelIds);
        accessStatus = "OK";
        accessReason = applicableReadPolicyCount == 0
            ? "没有匹配到当前有效的已审批 READ 规则；此数量不代表特定用户的最终访问决策"
            : "仅统计匹配该对象坐标或分级的已审批 READ 规则，不代表特定用户的最终访问决策";
      } catch (RuntimeException ex) {
        accessStatus = "UNAVAILABLE";
        accessReason = "无法读取当前项目的访问策略";
      }
    }

    List<ClassificationFact> facts = classifications.stream()
        .map(view -> ClassificationFact.from(view, resolvedMaskingCoverage.get(view.objectKey())))
        .toList();
    String complianceReason = "合规检查结果按批次归属，当前没有该实体的最新检查结果";
    return new SecurityClassificationSummary(
        facts.size(), facts, applicableReadPolicyCount, accessStatus, accessReason,
        classifications.isEmpty() || "UNAVAILABLE".equals(maskingStatus) ? null : maskedCount,
        classifications.isEmpty() || "UNAVAILABLE".equals(maskingStatus) ? null : unmaskedCount,
        maskingStatus, maskingReason, "UNAVAILABLE", complianceReason,
        "PARTIAL", "分级、访问规则与脱敏匹配分别展示；访问规则数量不代表有效访问决策");
  }

  private static Long positiveLong(String value) {
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      long parsed = Long.parseLong(value);
      return parsed > 0 ? parsed : null;
    } catch (NumberFormatException ex) {
      return null;
    }
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
        new SectionAction("打开安全分级分类", target, sourceId),
        new SectionAction("打开访问策略", "/data-security/access", sourceId),
        new SectionAction("打开脱敏策略", "/data-security/masking", sourceId),
        new SectionAction("查看批次合规检查", "/data-security/compliance", sourceId));
    return new io.yak.ops.business.security.asset.SecuritySectionContract(
        status, summary, reason, actions,
        status == SectionStatus.EMPTY ? List.of()
            : List.of(new SectionEvidence("SECURITY", sourceId, observedAt)),
        new SectionProvenance("SECURITY", sourceId, observedAt), observedAt,
        new SectionCapability(true, status != SectionStatus.UNAVAILABLE, reason));
  }
}
