package io.yak.ops.business.asset.application;

import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetStatusTtlFacts;
import io.yak.ops.business.asset.reconcile.AssetProviderRegistry;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.quality.domain.QualityDomain.TableMonitorSummary;
import io.yak.ops.business.quality.monitor.QualityMonitorReader;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.spi.section.SectionContext;
import io.yak.ops.spi.section.SectionContract;
import io.yak.ops.spi.section.SectionProvider;
import io.yak.ops.spi.section.SectionType;
import io.yak.framework.security.service.RbacPermissionService;
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
 * 360° 详情聚合(ticket 97,design §6.4):本体必成,其余分区独立容错,
 * 每块 {status: OK|UNAVAILABLE};缺失依赖如实标注不伪造(§6.3 口径)。
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
  private final ObjectProvider<SecurityClassificationQueryApi> securityQuery;
  private final ObjectProvider<RbacPermissionService> rbacPermissionServices;
  private final ObjectProvider<QualityMonitorReader> qualityReader;
  private final ObjectProvider<AssetStatusTtlFacts> ttlFacts;
  private final ObjectProvider<SectionProvider> sectionProviders;

  /** Five-state section result; note explains every state other than OK. */
  public record SectionView(String status, String note, Object data) {

    static SectionView ok(Object data) {
      return new SectionView("OK", null, data);
    }

    static SectionView unavailable(String note) {
      return new SectionView("UNAVAILABLE", note, null);
    }

    static SectionView empty(String note) {
      return new SectionView("EMPTY", note, Map.of());
    }

    static SectionView notApplicable(String note) {
      return new SectionView("NOT_APPLICABLE", note, null);
    }

    static SectionView denied() {
      return new SectionView("PERMISSION_DENIED", "当前用户无权查看该治理证据", null);
    }
  }

  public record AssetDetailView(AssetAppService.AssetView asset, Map<String, SectionView> sections) {}

  public AssetDetailView detail(Long id) {
    return detail(id, null);
  }

  public AssetDetailView detail(Long id, String operator) {
    AssetItemPO po = assetAppService.requireItem(id);
    Map<String, SectionView> sections = new LinkedHashMap<>();
    sections.put("statusFlow", SectionView.ok(statusFlowService.flow(po)));
    sections.put("sourceAttrs", hasSourceAttrsPermission(operator, po)
        ? sourceAttrs(po) : SectionView.denied());
    sections.put("lineage", lineage(po));
    sections.put("security", hasSectionPermission(operator, "SECURITY")
        ? security(po) : SectionView.denied());
    sections.put("fields", isPhysicalTable(po)
        ? SectionView.unavailable("字段目录读取入口尚未接入")
        : SectionView.notApplicable("当前资产类型没有物理表字段"));
    sections.put("quality", isPhysicalTable(po)
        ? (hasSectionPermission(operator, "QUALITY") ? quality(po) : SectionView.denied())
        : SectionView.notApplicable("当前 MVP 仅物理表纳入质量管理"));
    sections.put("ttl", AssetSourceType.MODEL.name().equals(po.getSourceType())
        ? (hasSectionPermission(operator, "LIFECYCLE") ? lifecycle(po) : SectionView.denied())
        : SectionView.notApplicable("当前 MVP 生命周期只适用于 Model"));
    sections.put("trend", SectionView.ok(viewRecordService.trend(po.getId(), TREND_DAYS)));
    sections.put("health", health(po));
    return new AssetDetailView(AssetAppService.toView(po), sections);
  }

  /** Reads one independently degradable section for progressive Asset Detail loading. */
  public SectionView section(Long id, String sectionType, String operator) {
    long startedAt = System.nanoTime();
    AssetItemPO po = assetAppService.requireItem(id);
    String key = sectionType == null ? "" : sectionType.trim().toUpperCase();
    if ("QUALITY".equals(key) && !isPhysicalTable(po)) {
      return SectionView.notApplicable("当前 MVP 仅物理表接入质量治理");
    }
    if ("LIFECYCLE".equals(key) && !AssetSourceType.MODEL.name().equals(po.getSourceType())) {
      return SectionView.notApplicable("当前 MVP 生命周期事实仅适用于 Model");
    }
    if ("TECHNICAL_METADATA".equals(key) && !isPhysicalTable(po)) {
      return SectionView.notApplicable("当前 MVP 技术元数据分区仅适用于物理表");
    }
    if (!hasSectionPermission(operator, key)) {
      return SectionView.denied();
    }
    SectionView result;
    try {
      result = switch (key) {
      case "OVERVIEW" -> SectionView.ok(Map.of(
          "assetKey", po.getAssetKey(), "name", po.getName(),
          "sourceType", po.getSourceType(), "sourceId", po.getSourceId()));
      case "GOVERNANCE" -> SectionView.ok(Map.of(
          "status", po.getStatus(),
          "owner", po.getOwner() == null ? "" : po.getOwner(),
          "directoryId", po.getDirectoryId() == null ? "" : po.getDirectoryId()));
      case "TECHNICAL_METADATA" -> technicalMetadata(po);
      case "QUALITY" -> quality(po);
      case "SECURITY" -> security(po);
      case "LINEAGE" -> lineage(po);
      case "USAGE" -> usage(po);
      case "LIFECYCLE" -> lifecycle(po);
        default -> throw new IllegalArgumentException("未知资产分区: " + sectionType);
      };
    } catch (RuntimeException e) {
      result = SectionView.unavailable("分区查询失败");
      LOG.warn("asset section query failed, assetId={}, sectionType={}, errorType={}",
          id, key, e.getClass().getSimpleName());
    }
    LOG.info("asset section query completed, assetId={}, sectionType={}, status={}, durationMs={}",
        id, key, result.status(), (System.nanoTime() - startedAt) / 1_000_000);
    return result;
  }

  private SectionView quality(AssetItemPO po) {
    QualityMonitorReader reader = qualityReader.getIfAvailable();
    if (reader == null) {
      return SectionView.unavailable("质量读侧未装配");
    }
    try {
      String dataSourceId = null;
      String database = null;
      String schema = null;
      String table = null;
      Optional<AssetDescriptor> descriptor = providerRegistry.find(AssetSourceType.METADATA)
          .flatMap(provider -> provider.refresh(po.getSourceId()));
      if (descriptor.isPresent()) {
        Map<String, String> extra = descriptor.get().extra();
        dataSourceId = extra.get("dataSourceId");
        database = extra.get("databaseName");
        schema = extra.get("schemaName");
        table = extra.get("tableName");
      }
      if (!org.springframework.util.StringUtils.hasText(dataSourceId)
          || !org.springframework.util.StringUtils.hasText(database)
          || !org.springframework.util.StringUtils.hasText(table)) {
        return SectionView.unavailable("物理表位置不完整，无法查询质量证据");
      }
      String resolvedTable = table;
      List<TableMonitorSummary> matches = reader.tableSummaries(
              Long.parseLong(dataSourceId), database, schema).stream()
          .filter(summary -> resolvedTable.equalsIgnoreCase(summary.tableName()))
          .toList();
      if (matches.isEmpty()) {
        return SectionView.empty("该物理表尚未纳入质量监控");
      }
      return SectionView.ok(Map.of(
          "monitorCount", matches.size(),
          "monitors", matches.stream().map(summary -> Map.of(
              "monitorId", summary.monitorId() == null ? "" : summary.monitorId(),
              "monitorName", summary.monitorName() == null ? "" : summary.monitorName(),
              "ruleCount", summary.ruleCount(),
              "lastResult", summary.lastResult() == null ? "NOT_RUN" : summary.lastResult().name(),
              "lastRunTime", summary.lastRunTime() == null ? "" : summary.lastRunTime())).toList()));
    } catch (RuntimeException e) {
      return SectionView.unavailable("质量查询暂不可用，请稍后重试");
    }
  }

  private SectionView lifecycle(AssetItemPO po) {
    AssetStatusTtlFacts api = ttlFacts.getIfAvailable();
    if (api == null) {
      return SectionView.unavailable("生命周期读侧未装配");
    }
    try {
      Optional<AssetStatusTtlFacts.TtlFacts> result = api.ttlFacts(po.getSourceId());
      if (result.isEmpty()) {
        return SectionView.unavailable("生命周期域无法解析该模型");
      }
      AssetStatusTtlFacts.TtlFacts facts = result.get();
      if (!facts.policyApplied()) {
        return SectionView.empty("当前模型尚未绑定有效生命周期策略");
      }
      return SectionView.ok(Map.of(
          "policyCode", facts.policyCode() == null ? "" : facts.policyCode(),
          "bindingSource", facts.bindingSource() == null ? "" : facts.bindingSource(),
          "state", facts.state() == null ? "" : facts.state()));
    } catch (RuntimeException e) {
      return SectionView.unavailable("生命周期查询暂不可用，请稍后重试");
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
      default -> null;
    };
    return permission == null || hasPermission(operator, permission);
  }

  private static boolean isPhysicalTable(AssetItemPO po) {
    return AssetSourceType.METADATA.name().equals(po.getSourceType());
  }

  private SectionView technicalMetadata(AssetItemPO po) {
    SectionContext context =
        new SectionContext(po.getAssetKey(), po.getSourceType(), po.getSourceId());
    Optional<SectionProvider> provider = sectionProviders.orderedStream()
        .filter(candidate -> candidate.sectionType() == SectionType.TECHNICAL_METADATA)
        .filter(candidate -> candidate.supports(context))
        .findFirst();
    if (provider.isEmpty()) {
      return SectionView.unavailable("技术元数据读取提供方未装配");
    }
    SectionContract contract = provider.get().query(context);
    return new SectionView(contract.status().name(), contract.reason(), contract);
  }

  private SectionView usage(AssetItemPO po) {
    Map<String, Object> data = new LinkedHashMap<>();
    try {
      data.put("pageActivity", Map.of(
          "ownerDomain", "ASSET",
          "status", "OK",
          "views", viewRecordService.trend(po.getId(), TREND_DAYS)));
    } catch (RuntimeException e) {
      data.put("pageActivity", Map.of(
          "ownerDomain", "ASSET",
          "status", "UNAVAILABLE",
          "reason", "资产页活动暂不可用"));
    }

    LineageQueryService service = lineageQuery.getIfAvailable();
    if (service == null) {
      data.put("structuralUsage", Map.of(
          "ownerDomain", "LINEAGE",
          "status", "UNAVAILABLE",
          "reason", "血缘服务未装配"));
    } else {
      try {
        LineageAsset root = service.getAssetByKey(po.getAssetKey());
        if (root == null) {
          data.put("structuralUsage", Map.of(
              "ownerDomain", "LINEAGE",
              "status", "UNAVAILABLE",
              "reason", "血缘域尚无该资产登记"));
        } else {
          int downstreamCount =
              service.graph(root.id(), LineageDirection.DOWNSTREAM, LINEAGE_HOP)
                  .relations().size();
          data.put("structuralUsage", Map.of(
              "ownerDomain", "LINEAGE",
              "status", "OK",
              "downstreamReferenceCount", downstreamCount));
        }
      } catch (RuntimeException e) {
        data.put("structuralUsage", Map.of(
            "ownerDomain", "LINEAGE",
            "status", "UNAVAILABLE",
            "reason", "结构引用暂不可用"));
      }
    }
    data.put("businessConsumption", Map.of(
        "status", "UNAVAILABLE",
        "reason", "当前尚未接入消费域的调用/引用读侧"));
    return SectionView.ok(data);
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

  /** 安全块:分级按对象键查;台账键与物理定级对象键不同源时如实 N/A。 */
  private SectionView security(AssetItemPO po) {
    SecurityClassificationQueryApi api = securityQuery.getIfAvailable();
    if (api == null) {
      return SectionView.unavailable("安全域未装配");
    }
    try {
      ClassificationView view = api.find(po.getAssetKey());
      return view == null
          ? SectionView.unavailable("未定级,或该资产无对应物理定级对象")
          : SectionView.ok(view);
    } catch (RuntimeException e) {
      return SectionView.unavailable("安全查询暂不可用，请稍后重试");
    }
  }
}
