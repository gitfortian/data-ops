package io.yak.ops.business.asset.application;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.asset.api.AssetGovernanceQueryApi;
import io.yak.ops.business.asset.api.AssetSectionResult;
import io.yak.ops.business.asset.controller.v1.dto.AssetRequests.ItemQueryDTO;
import io.yak.ops.common.constant.asset.AssetPermissionCode;
import io.yak.ops.core.security.ActionAuthorization;
import io.yak.ops.spi.section.SectionType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reuses the Asset ledger and five-state section reads, without source-domain commands. */
@Component
@RequiredArgsConstructor
public class AssetGovernanceQueryAdapter implements AssetGovernanceQueryApi {
  private final AssetAppService assets;
  private final AssetDiscoverService discovery;
  private final ActionAuthorization authorization;

  @Override
  public List<AssetFact> search(String keyword, int limit) {
    authorization.requirePermission(AssetPermissionCode.READ);
    if (keyword == null || keyword.isBlank() || keyword.length() > 128) {
      throw new IllegalArgumentException("资产关键词应为 1 到 128 个字符");
    }
    ItemQueryDTO query = new ItemQueryDTO();
    query.setKeyword(keyword.trim());
    query.setPageSize(Math.min(20, Math.max(1, limit)));
    return assets.page(query).records().stream().map(AssetGovernanceQueryAdapter::fact).toList();
  }

  @Override
  public AssetFact require(long assetId) {
    authorization.requirePermission(AssetPermissionCode.READ);
    return fact(assets.get(assetId));
  }

  @Override
  public AssetSectionResult section(long assetId, SectionType sectionType) {
    authorization.requirePermission(AssetPermissionCode.READ);
    var asset = assets.get(assetId);
    var view = discovery.section(assetId, sectionType.name(), YakSecurityContext.getCurrentUsername());
    return AssetSectionProjector.project(sectionType, view, asset);
  }

  private static AssetFact fact(AssetAppService.AssetView asset) {
    return new AssetFact(asset.id(), asset.assetKey(), asset.sourceType(), asset.sourceId(),
        asset.name(), asset.description(), asset.owner(), asset.status(), asset.updateTime(),
        AssetSnapshotFingerprint.of(asset.id(), asset.name(), asset.description(), asset.accessUri()));
  }
}
