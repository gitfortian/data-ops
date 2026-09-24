package io.yak.ops.business.security.asset;

import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.business.security.config.ConditionalOnSecurityPersistence;
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
            || !StringUtils.hasText(context.attributes().get("tableName")))) {
      return response(context, SectionStatus.UNAVAILABLE, Map.of(),
          "Metadata 未提供完整数据库与表坐标，无法准确匹配 Security 对象", observedAt);
    }
    List<ClassificationView> classifications = classifications(context);
    if (classifications.isEmpty()) {
      return response(context, SectionStatus.EMPTY, Map.of(),
          "Security 域没有返回该对象的分级记录", observedAt);
    }

    List<Map<String, Object>> evidence = classifications.stream().map(view -> {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("objectKey", view.objectKey());
      row.put("levelCode", view.levelCode());
      row.put("levelName", view.levelName());
      row.put("levelRank", view.levelRank());
      row.put("categoryCode", view.categoryCode());
      row.put("categoryName", view.categoryName());
      return row;
    }).toList();
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("classificationCount", evidence.size());
    values.put("classifications", evidence);
    values.put("strategySummaryStatus", "UNAVAILABLE");
    values.put("strategySummaryReason",
        "当前 Security 稳定读接口不提供对象级访问策略与脱敏匹配摘要");
    values.put("classificationStateLimitation",
        "读接口未返回分类记录生命周期状态；请在 Security 分级分类工作台核对记录状态");
    return response(context, SectionStatus.OK, values, null, observedAt);
  }

  private List<ClassificationView> classifications(SectionContext context) {
    String database = context.attributes().get("databaseName");
    String table = context.attributes().get("tableName");
    if (StringUtils.hasText(database) && StringUtils.hasText(table)) {
      return classificationQuery.findByTable(database, table);
    }
    ClassificationView exact = classificationQuery.find(context.assetKey());
    return exact == null ? List.of() : List.of(exact);
  }

  private static SectionContract response(
      SectionContext context, SectionStatus status, Map<String, Object> values,
      String reason, Instant observedAt) {
    String sourceId = context.assetKey();
    String returnAssetId = context.attributes().get("returnAssetId");
    String target = "/data-security/classification";
    if (returnAssetId != null && returnAssetId.matches("\\d+")) {
      target += "?returnAssetId=" + returnAssetId;
    }
    List<SectionAction> actions = List.of(
        new SectionAction("打开安全分级分类", target, sourceId));
    return new io.yak.ops.business.security.asset.SecuritySectionContract(
        status, values, reason, actions,
        status == SectionStatus.EMPTY ? List.of()
            : List.of(new SectionEvidence("SECURITY", sourceId, observedAt)),
        new SectionProvenance("SECURITY", sourceId, observedAt), observedAt,
        new SectionCapability(true, status != SectionStatus.UNAVAILABLE, reason));
  }
}
