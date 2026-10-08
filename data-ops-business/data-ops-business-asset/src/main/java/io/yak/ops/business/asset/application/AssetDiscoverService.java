package io.yak.ops.business.asset.application;

import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.reconcile.AssetProviderRegistry;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetStatus;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.common.constant.metric.MetricPermissionCode;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionAction;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionStatus;
import io.yak.ops.spi.section.SectionSummary;
import io.yak.ops.spi.section.SectionType;
import io.yak.framework.security.service.RbacPermissionService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Asset ledger detail stays local and fast; source-owned evidence is read independently per section.
 */
@Service
@RequiredArgsConstructor
public class AssetDiscoverService {

  private static final Logger LOG = LoggerFactory.getLogger(AssetDiscoverService.class);
  private static final int LINEAGE_HOP = 1;
  private static final int TREND_DAYS = 30;

  private final AssetAppService assetAppService;
  private final AssetProviderRegistry providerRegistry;
  private final AssetViewRecordService viewRecordService;
  private final AssetStatusFlowService statusFlowService;
  private final ObjectProvider<LineageQueryService> lineageQuery;
  private final ObjectProvider<RbacPermissionService> rbacPermissionServices;
  private final ObjectProvider<SectionProvider> sectionProviders;

  /** Five-state section result; note explains every state other than OK. */
  public record SectionView(SectionStatus status, String note, Object data, List<SectionAction> actions) {

    public SectionView(SectionStatus status, String note, Object data) {
      this(status, note, data, List.of());
    }

    public SectionView {
      actions = actions == null ? List.of() : List.copyOf(actions);
    }

    static SectionView ok(Object data) {
      return new SectionView(SectionStatus.OK, null, data);
    }

    static SectionView unavailable(String note) {
      return new SectionView(SectionStatus.UNAVAILABLE, note, null);
    }

    static SectionView empty(String note) {
      return new SectionView(SectionStatus.EMPTY, note, Map.of());
    }

    static SectionView notApplicable(String note) {
      return new SectionView(SectionStatus.NOT_APPLICABLE, note, null);
    }

    static SectionView denied() {
      return new SectionView(SectionStatus.PERMISSION_DENIED, "当前用户无权查看该治理证据", null);
    }
  }

  public record AssetDetailView(AssetAppService.AssetView asset, Map<String, SectionView> sections) {}

  public record OverviewSummary(
      String assetKey, String name, AssetSourceType sourceType, String sourceId)
      implements SectionSummary {
    @Override public Map<String, Object> values() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("assetKey", assetKey);
      values.put("name", name);
      values.put("sourceType", sourceType);
      values.put("sourceId", sourceId);
      return values;
    }
  }

  public record GovernanceSummary(AssetStatus status, String owner, Long directoryId)
      implements SectionSummary {
    @Override public Map<String, Object> values() {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put("status", status);
      values.put("owner", owner);
      values.put("directoryId", directoryId);
      return values;
    }
  }

  public enum UsageOwnerDomain { ASSET, LINEAGE, METRIC, CONSUMING_DOMAINS }

  public record PageActivityUsage(
      UsageOwnerDomain ownerDomain, SectionStatus status, int windowDays, String meaning,
      Long viewCount, Long distinctViewerCount, LocalDateTime lastViewedAt,
      List<AssetViewRecordService.DailyView> views, String reason) {}

  public record StructuralUsage(
      UsageOwnerDomain ownerDomain, SectionStatus status, String direction, int hop,
      Integer downstreamReferenceCount, String reason) {}

  public record BusinessConsumption(
      UsageOwnerDomain ownerDomain, SectionStatus status, String scope, Integer totalCount,
      Integer reportCount, Integer datasetCount, Integer dashboardCount, Integer apiCount,
      Integer screenCount, Integer consumerCount, Integer userCount, Integer teamCount,
      Integer dataServiceCount, Integer jobCount, Integer successfulUsageCount,
      Integer activeSubscriptionCount, String lastObservedAt, String coverageNote, String reason) {}

  public record UsageSummary(
      PageActivityUsage pageActivity,
      StructuralUsage structuralUsage,
      BusinessConsumption businessConsumption) implements SectionSummary {
    @Override public Map<String, Object> values() {
      return Map.of(
          "pageActivity", pageActivity,
          "structuralUsage", structuralUsage,
          "businessConsumption", businessConsumption);
    }
  }

  public AssetDetailView detail(Long id) {
    return detail(id, null);
  }

  public AssetDetailView detail(Long id, String operator) {
    AssetItemPO po = assetAppService.requireItem(id);
    Map<String, SectionView> sections = new LinkedHashMap<>();
    sections.put("statusFlow", SectionView.ok(statusFlowService.flow(po)));
    sections.put("trend", SectionView.ok(viewRecordService.trend(po.getId(), TREND_DAYS)));
    sections.put("health", health(po));
    return new AssetDetailView(AssetAppService.toView(po), sections);
  }

  /** Reads source-owned overview attributes independently from the Asset ledger response. */
  public SectionView sourceAttributes(Long id, String operator) {
    AssetItemPO po = assetAppService.requireItem(id);
    return hasSourceAttrsPermission(operator, po) ? sourceAttrs(po) : SectionView.denied();
  }

  /** Reads one independently degradable section for progressive Asset Detail loading. */
  public SectionView section(Long id, String sectionType, String operator) {
    long startedAt = System.nanoTime();
    AssetItemPO po = assetAppService.requireItem(id);
    String key = sectionType == null ? "" : sectionType.trim().toUpperCase();
    if ("QUALITY".equals(key) && !isPhysicalTable(po)) {
      logSectionCompletion(id, key, "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", startedAt);
      return SectionView.notApplicable("当前 MVP 仅物理表接入质量治理");
    }
    if ("LIFECYCLE".equals(key) && !AssetSourceType.MODEL.name().equals(po.getSourceType())) {
      logSectionCompletion(id, key, "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", startedAt);
      return SectionView.notApplicable("当前 MVP 生命周期事实仅适用于 Model");
    }
    if ("TECHNICAL_METADATA".equals(key) && !isPhysicalTable(po)) {
      logSectionCompletion(id, key, "NOT_APPLICABLE", "NOT_APPLICABLE", "NOT_APPLICABLE", startedAt);
      return SectionView.notApplicable("当前 MVP 技术元数据分区仅适用于物理表");
    }
    if ("USAGE".equals(key) && AssetSourceType.METRIC.name().equals(po.getSourceType())
        && !hasPermission(operator, MetricPermissionCode.READ)) {
      logSectionCompletion(id, key, "PERMISSION_DENIED", "NOT_INVOKED_PERMISSION",
          "PERMISSION_DENIED", startedAt);
      return SectionView.denied();
    }
    if (!hasSectionPermission(operator, key)) {
      logSectionCompletion(id, key, "PERMISSION_DENIED", "NOT_INVOKED_PERMISSION",
          "PERMISSION_DENIED", startedAt);
      return SectionView.denied();
    }
    SectionView result;
    String providerStatus = "NOT_REQUIRED";
    try {
      result = switch (key) {
      case "OVERVIEW" -> SectionView.ok(new OverviewSummary(
          po.getAssetKey(), po.getName(), AssetSourceType.valueOf(po.getSourceType()), po.getSourceId()));
      case "GOVERNANCE" -> SectionView.ok(new GovernanceSummary(
          AssetStatus.valueOf(po.getStatus()), po.getOwner(), po.getDirectoryId()));
      case "TECHNICAL_METADATA" -> providerSection(po, SectionType.TECHNICAL_METADATA);
      case "QUALITY" -> quality(po);
      case "SECURITY" -> providerSection(po, SectionType.SECURITY);
      case "LINEAGE" -> lineage(po);
      case "USAGE" -> usage(po);
      case "LIFECYCLE" -> providerSection(po, SectionType.LIFECYCLE);
        default -> throw new IllegalArgumentException("未知资产分区: " + sectionType);
      };
      if (result.status() == SectionStatus.PERMISSION_DENIED) {
        providerStatus = "NOT_INVOKED_PERMISSION";
      } else if (result.status() == SectionStatus.NOT_APPLICABLE) {
        providerStatus = "NOT_APPLICABLE";
      } else if ("USAGE".equals(key) && result.data() instanceof UsageSummary usage) {
        boolean anyFactAvailable = List.of(usage.pageActivity().status(),
                usage.structuralUsage().status(), usage.businessConsumption().status()).stream()
            .anyMatch(status -> status == SectionStatus.OK || status == SectionStatus.EMPTY);
        providerStatus = anyFactAvailable ? "AVAILABLE" : "DEGRADED";
      } else if ("TECHNICAL_METADATA".equals(key) || "QUALITY".equals(key)
          || "SECURITY".equals(key) || "LIFECYCLE".equals(key)) {
        providerStatus = result.status() == SectionStatus.OK || result.status() == SectionStatus.EMPTY
            ? "AVAILABLE" : "DEGRADED";
      }
    } catch (RuntimeException e) {
      result = SectionView.unavailable("分区查询失败");
      providerStatus = "DEGRADED";
      LOG.warn("asset section query failed, assetId={}, sectionType={}, errorType={}",
          id, key, e.getClass().getSimpleName());
    }
    String failureReason = result.status() == SectionStatus.OK || result.status() == SectionStatus.EMPTY
        ? ("PARTIAL".equals(providerStatus) ? "CONSUMER_READ_SIDE_UNAVAILABLE" : "NONE")
        : result.status().name();
    logSectionCompletion(id, key, result.status().name(), providerStatus, failureReason, startedAt);
    return result;
  }

  private static void logSectionCompletion(
      Long assetId, String sectionType, String status, String providerStatus,
      String failureReason, long startedAt) {
    LOG.info("asset section query completed, assetId={}, sectionType={}, status={}, providerStatus={}, failureReason={}, durationMs={}",
        assetId, sectionType, status, providerStatus, failureReason,
        (System.nanoTime() - startedAt) / 1_000_000);
  }

  private SectionView quality(AssetItemPO po) {
    try {
      Map<String, String> coordinates = metadataAttributes(po, SectionType.QUALITY);
      if (coordinates == null) {
        return SectionView.unavailable("物理表位置不完整，无法查询质量证据");
      }
      if (!org.springframework.util.StringUtils.hasText(coordinates.get("dataSourceId"))
          || !org.springframework.util.StringUtils.hasText(coordinates.get("databaseName"))
          || !org.springframework.util.StringUtils.hasText(coordinates.get("tableName"))) {
        return SectionView.unavailable("物理表位置不完整，无法查询质量证据");
      }
      Map<String, String> attributes = new LinkedHashMap<>(coordinates);
      attributes.put("returnAssetId", String.valueOf(po.getId()));
      return providerSection(po, SectionType.QUALITY, attributes);
    } catch (RuntimeException e) {
      LOG.warn("asset section query failed, assetId={}, sectionType=QUALITY, errorType={}",
          po.getId(), e.getClass().getSimpleName());
      return SectionView.unavailable("质量查询暂不可用，请稍后重试");
    }
  }

  private boolean hasSectionPermission(String operator, String sectionType) {
    if (operator == null || operator.isBlank()) {
      return false;
    }
    if ("QUALITY".equals(sectionType)) {
      return hasPermission(operator, "quality:monitor:read")
          && hasPermission(operator, "quality:execution:read");
    }
    String permission = switch (sectionType) {
      case "TECHNICAL_METADATA" -> "data-metadata:read";
      case "SECURITY" -> "data-security:read";
      case "LIFECYCLE" -> "data-lifecycle:read";
      default -> null;
    };
    if (permission == null) {
      return true;
    }
    return hasPermission(operator, permission);
  }

  private boolean hasSourceAttrsPermission(String operator, AssetItemPO po) {
    if (operator == null || operator.isBlank()) {
      return false;
    }
    String permission = switch (po.getSourceType() == null ? "" : po.getSourceType()) {
      case "MODEL" -> "modeling:read";
      case "METRIC" -> "metric:read";
      case "METADATA" -> "data-metadata:read";
      case "DATASET", "TASK" -> "data-development:read";
      case "DATA_SERVICE" -> "data-service:read";
      default -> null;
    };
    return permission == null || hasPermission(operator, permission);
  }

  private static boolean isPhysicalTable(AssetItemPO po) {
    return AssetSourceType.METADATA.name().equals(po.getSourceType());
  }

  private SectionView providerSection(AssetItemPO po, SectionType type) {
    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("returnAssetId", String.valueOf(po.getId()));
    if (AssetSourceType.METADATA.name().equals(po.getSourceType())) {
      Map<String, String> metadataAttributes = metadataAttributes(po, type);
      if (metadataAttributes == null) {
        return SectionView.unavailable("物理表坐标暂不可用，无法读取该分区");
      }
      attributes.putAll(metadataAttributes);
    }
    return providerSection(po, type, attributes);
  }

  private Map<String, String> metadataAttributes(AssetItemPO po, SectionType sectionType) {
    try {
      return providerRegistry.find(AssetSourceType.METADATA)
          .flatMap(provider -> provider.refresh(po.getSourceId()))
          .map(AssetDescriptor::extra)
          .orElse(null);
    } catch (RuntimeException e) {
      LOG.warn("asset section context resolution failed, assetId={}, sectionType={}, errorType={}",
          po.getId(), sectionType, e.getClass().getSimpleName());
      return null;
    }
  }

  private SectionView providerSection(
      AssetItemPO po, SectionType type, Map<String, String> attributes) {
    SectionContext context = sectionContext(po, attributes);
    SectionContract contract = querySectionProvider(po, type, context);
    return contract == null
        ? SectionView.unavailable(type + " 分区读取提供方未装配或暂不可用")
        : new SectionView(contract.status(), contract.reason(), contract);
  }

  private static SectionContext sectionContext(AssetItemPO po, Map<String, String> attributes) {
    return new SectionContext(po.getAssetKey(), po.getSourceType(), po.getSourceId(), attributes);
  }

  private SectionContract querySectionProvider(
      AssetItemPO po, SectionType type, SectionContext context) {
    Optional<SectionProvider> provider = sectionProviders.orderedStream()
        .filter(candidate -> candidate.sectionType() == type)
        .filter(candidate -> candidate.supports(context))
        .findFirst();
    if (provider.isEmpty()) {
      LOG.info("asset section provider unavailable, assetId={}, sectionType={}, providerStatus=NOT_REGISTERED",
          po.getId(), type);
      return null;
    }
    SectionProvider selected = provider.get();
    long startedAt = System.nanoTime();
    try {
      SectionContract contract = selected.query(context);
      LOG.info("asset section provider completed, assetId={}, sectionType={}, provider={}, status={}, durationMs={}",
          po.getId(), type, selected.getClass().getSimpleName(), contract.status(),
          (System.nanoTime() - startedAt) / 1_000_000);
      return contract;
    } catch (RuntimeException e) {
      LOG.warn("asset section provider failed, assetId={}, sectionType={}, provider={}, errorType={}",
          po.getId(), type, selected.getClass().getSimpleName(), e.getClass().getSimpleName());
      return null;
    }
  }

  private SectionView usage(AssetItemPO po) {
    PageActivityUsage pageActivity;
    try {
      AssetViewRecordService.ActivitySummary activity =
          viewRecordService.summary(po.getId(), TREND_DAYS);
      pageActivity = new PageActivityUsage(UsageOwnerDomain.ASSET, SectionStatus.OK, TREND_DAYS,
          "资产详情页访问活动，不代表业务消费", activity.viewCount(),
          activity.distinctViewerCount(), activity.lastViewedAt(),
          viewRecordService.trend(po.getId(), TREND_DAYS), null);
    } catch (RuntimeException e) {
      pageActivity = new PageActivityUsage(UsageOwnerDomain.ASSET, SectionStatus.UNAVAILABLE,
          TREND_DAYS, "资产详情页访问活动，不代表业务消费", null, null, null,
          List.of(), "资产页活动暂不可用");
    }

    StructuralUsage structuralUsage;
    LineageQueryService service = lineageQuery.getIfAvailable();
    if (service == null) {
      structuralUsage = new StructuralUsage(UsageOwnerDomain.LINEAGE, SectionStatus.UNAVAILABLE,
          "DOWNSTREAM", LINEAGE_HOP, null, "血缘服务未装配");
    } else {
      try {
        LineageAsset root = service.getAssetByKey(po.getAssetKey());
        if (root == null) {
          structuralUsage = new StructuralUsage(UsageOwnerDomain.LINEAGE,
              SectionStatus.EMPTY, "DOWNSTREAM", LINEAGE_HOP, 0,
              "血缘域尚无该资产登记");
        } else {
          int downstreamCount =
              Math.toIntExact(service.downstreamRelationCount(root.id()));
          structuralUsage = new StructuralUsage(UsageOwnerDomain.LINEAGE, SectionStatus.OK,
              "DOWNSTREAM", LINEAGE_HOP, downstreamCount, null);
        }
      } catch (RuntimeException e) {
        structuralUsage = new StructuralUsage(UsageOwnerDomain.LINEAGE, SectionStatus.UNAVAILABLE,
            "DOWNSTREAM", LINEAGE_HOP, null, "结构引用暂不可用");
      }
    }
    BusinessConsumption businessConsumption;
    List<SectionAction> actions = new ArrayList<>();
    SectionContext context = sectionContext(po, Map.of("returnAssetId", String.valueOf(po.getId())));
    SectionContract contract = querySectionProvider(po, SectionType.USAGE, context);
    if (contract != null) {
        Map<String, Object> values = contract.summary() == null
            ? Map.of() : contract.summary().values();
        businessConsumption = new BusinessConsumption(
            usageOwner(contract.ownerDomain()), contract.status(),
            stringValue(values.get("scope")), integerValue(values.get("totalCount")),
            integerValue(values.get("reportCount")), integerValue(values.get("datasetCount")),
            integerValue(values.get("dashboardCount")), integerValue(values.get("apiCount")),
            integerValue(values.get("screenCount")), integerValue(values.get("consumerCount")),
            integerValue(values.get("userCount")), integerValue(values.get("teamCount")),
            integerValue(values.get("dataServiceCount")), integerValue(values.get("jobCount")),
            integerValue(values.get("successfulUsageCount")),
            integerValue(values.get("activeSubscriptionCount")),
            stringValue(values.get("lastObservedAt")), stringValue(values.get("coverageNote")),
            contract.reason());
        actions.addAll(contract.actions());
    } else {
      businessConsumption = new BusinessConsumption(UsageOwnerDomain.CONSUMING_DOMAINS,
          SectionStatus.UNAVAILABLE, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null,
          "当前资产类型尚未接入消费域读侧");
    }
    UsageSummary summary = new UsageSummary(pageActivity, structuralUsage, businessConsumption);
    SectionStatus status = List.of(pageActivity.status(), structuralUsage.status(),
            businessConsumption.status()).stream()
        .anyMatch(child -> child == SectionStatus.OK || child == SectionStatus.EMPTY)
        ? SectionStatus.OK : SectionStatus.UNAVAILABLE;
    return new SectionView(status, status == SectionStatus.UNAVAILABLE
        ? "所有使用事实来源当前均不可用" : null, summary, actions);
  }

  private static UsageOwnerDomain usageOwner(String owner) {
    try {
      return UsageOwnerDomain.valueOf(owner);
    } catch (IllegalArgumentException | NullPointerException e) {
      return UsageOwnerDomain.CONSUMING_DOMAINS;
    }
  }

  private static String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static Integer integerValue(Object value) {
    return value instanceof Number number ? number.intValue() : null;
  }

  private boolean hasPermission(String operator, String permission) {
    return rbacPermissionServices.orderedStream()
        .anyMatch(service -> service.hasPermission(operator, permission));
  }

  /** 健康度:派生缓存 + 评分明细(§6.3 口径,不可手改). */
  private SectionView health(AssetItemPO po) {
    if (po.getHealthScore() == null) {
      return SectionView.unavailable("尚未完成首次健康度计算");
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("score", po.getHealthScore());
    data.put("grade", po.getHealthGrade());
    data.put("detail", po.getHealthDetail());
    return SectionView.ok(data);
  }

  /** 源域属性:provider.refresh 实时读;指纹与台账不一致时标"源已变更"(待确认语义)。 */
  private SectionView sourceAttrs(AssetItemPO po) {
    if (AssetSourceType.MANUAL.name().equals(po.getSourceType())) {
      return SectionView.unavailable("手工登记资产无源域,本体即事实");
    }
    try {
      Optional<AssetSourceType> type = parseSourceType(po.getSourceType());
      if (type.isEmpty()) {
        return SectionView.unavailable("未知来源域 " + po.getSourceType());
      }
      Optional<AssetDescriptor> fresh = providerRegistry.find(type.get())
          .flatMap(provider -> provider.refresh(po.getSourceId()));
      if (fresh.isEmpty()) {
        return SectionView.unavailable("源对象不存在或提供方未就绪,等待对账判定");
      }
      AssetDescriptor d = fresh.get();
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("name", d.name());
      data.put("description", d.description());
      data.put("assetType", d.assetType());
      data.put("layerCode", d.layerCode());
      data.put("domainCode", d.domainCode());
      data.put("suggestedOwner", d.suggestedOwner());
      data.put("updatedAt", d.updatedAt());
      data.put("sourceChanged", !Objects.equals(po.getContentHash(), d.contentHash()));
      data.put("extra", d.extra());
      return SectionView.ok(data);
    } catch (RuntimeException e) {
      return SectionView.unavailable("源域暂不可用，请稍后重试");
    }
  }

  private Optional<AssetSourceType> parseSourceType(String raw) {
    try {
      return Optional.of(AssetSourceType.valueOf(raw));
    } catch (IllegalArgumentException | NullPointerException e) {
      return Optional.empty();
    }
  }

  /** 血缘局部图:键先解析为血缘资产,再取 1 跳双向子图(asset 不回写血缘)。 */
  private SectionView lineage(AssetItemPO po) {
    LineageQueryService service = lineageQuery.getIfAvailable();
    if (service == null) {
      return SectionView.unavailable("血缘服务未装配");
    }
    LineageAsset root;
    try {
      root = service.findAssetByKey(po.getAssetKey()).orElse(null);
    } catch (RuntimeException e) {
      return SectionView.unavailable("血缘查询暂不可用，请稍后重试");
    }
    if (root == null) {
      return SectionView.empty("该资产尚未登记血缘");
    }
    try {
      return SectionView.ok(service.graph(root.id(), LineageDirection.BOTH, LINEAGE_HOP));
    } catch (RuntimeException e) {
      return SectionView.unavailable("血缘查询暂不可用，请稍后重试");
    }
  }

}
