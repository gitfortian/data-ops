package io.yak.ops.business.asset.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.common.bean.po.asset.AssetItemPO;
import io.yak.ops.common.enums.asset.AssetSourceType;
import io.yak.ops.core.project.CurrentProject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Exact read-only lookup from an owning-domain stable identity to the Asset Registry projection.
 * This never creates or mutates an Asset and therefore does not become a second source of truth.
 */
@Service
@RequiredArgsConstructor
public class AssetSourceLookupService {

  private final CurrentProject currentProject;
  private final AssetItemMapper itemMapper;

  public SourceLookup lookup(String sourceType, String sourceId) {
    if (sourceId == null || sourceId.isBlank()) {
      throw new IllegalArgumentException("sourceId 不能为空");
    }
    final AssetSourceType type;
    try {
      type = AssetSourceType.valueOf(sourceType == null ? "" : sourceType.trim().toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("未知 Asset sourceType: " + sourceType, exception);
    }

    Long projectId = currentProject.requireProjectId();
    AssetItemPO po = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getSourceType, type.name())
        .eq(AssetItemPO::getSourceId, sourceId.trim())
        .eq(AssetItemPO::getDeleted, false)
        .last("LIMIT 1"));

    if (po == null) {
      return new SourceLookup(
          "NOT_INDEXED", type.name(), sourceId.trim(), null, null, null, null);
    }
    return new SourceLookup(
        "FOUND",
        po.getSourceType(),
        po.getSourceId(),
        po.getId(),
        po.getAssetKey(),
        po.getAssetType(),
        po.getStatus());
  }

  public record SourceLookup(
      String state,
      String sourceType,
      String sourceId,
      Long assetId,
      String assetKey,
      String assetType,
      String assetStatus) {}
}
