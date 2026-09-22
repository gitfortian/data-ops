package io.yak.ops.business.asset.application;

import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.reconcile.AssetProviderRegistry;
import io.yak.ops.business.lineage.domain.LineageAsset;
import io.yak.ops.business.lineage.domain.LineageDirection;
import io.yak.ops.business.lineage.query.LineageQueryService;
import io.yak.ops.business.security.api.ClassificationView;
import io.yak.ops.business.security.api.SecurityClassificationQueryApi;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 360° 详情聚合(ticket 97,design §6.4):本体必成,其余分区独立容错,
 * 每块 {status: OK|UNAVAILABLE};缺失依赖如实标注不伪造(§6.3 口径)。
 */
@Service
@RequiredArgsConstructor
public class AssetDiscoverService {

  private static final int LINEAGE_HOP = 1;
  private static final int TREND_DAYS = 30;

  private final AssetAppService assetAppService;
  private final AssetProviderRegistry providerRegistry;
  private final AssetViewRecordService viewRecordService;
  private final AssetStatusFlowService statusFlowService;
  private final ObjectProvider<LineageQueryService> lineageQuery;
  private final ObjectProvider<SecurityClassificationQueryApi> securityQuery;

  /** status: OK | UNAVAILABLE;note 为不可用原因(含 N/A 语义,如"MANUAL 无源域")。 */
  public record SectionView(String status, String note, Object data) {

    static SectionView ok(Object data) {
      return new SectionView("OK", null, data);
    }

    static SectionView unavailable(String note) {
      return new SectionView("UNAVAILABLE", note, null);
    }
  }

  public record AssetDetailView(AssetAppService.AssetView asset, Map<String, SectionView> sections) {}

  public AssetDetailView detail(Long id) {
    AssetItemPO po = assetAppService.requireItem(id);
    Map<String, SectionView> sections = new LinkedHashMap<>();
    sections.put("statusFlow", SectionView.ok(statusFlowService.flow(po)));
    sections.put("sourceAttrs", sourceAttrs(po));
    sections.put("lineage", lineage(po));
    sections.put("security", security(po));
    sections.put("fields", SectionView.unavailable("源域尚未提供字段级 SPI,详情页暂不展示"));
    sections.put("quality", SectionView.unavailable("质量域 SPI 未就绪(缺口 G1)"));
    sections.put("ttl", SectionView.unavailable("生命周期 TTL SPI 未就绪(缺口 G2)"));
    sections.put("trend", SectionView.ok(viewRecordService.trend(po.getId(), TREND_DAYS)));
    sections.put("health", health(po));
    return new AssetDetailView(AssetAppService.toView(po), sections);
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
      return SectionView.unavailable("源域调用失败: " + e.getMessage());
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
    try {
      LineageAsset root = service.getAssetByKey(po.getAssetKey());
      if (root == null) {
        return SectionView.unavailable("血缘域暂无该资产登记");
      }
      return SectionView.ok(service.graph(root.id(), LineageDirection.BOTH, LINEAGE_HOP));
    } catch (RuntimeException e) {
      return SectionView.unavailable("血缘查询失败: " + e.getMessage());
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
      return SectionView.unavailable("安全查询失败: " + e.getMessage());
    }
  }
}
