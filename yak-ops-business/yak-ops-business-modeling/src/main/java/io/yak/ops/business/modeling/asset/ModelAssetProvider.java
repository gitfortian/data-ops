package io.yak.ops.business.modeling.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.modeling.config.ConditionalOnModelingPersistence;
import io.yak.ops.business.modeling.dao.mapper.ModelingModelMapper;
import io.yak.ops.business.modeling.lineage.ModelingLineageRegistrationService;
import io.yak.ops.common.bean.po.modeling.ModelingModelPO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * MODEL 资产供给(asset ticket 94):只读本域模型表,asset_key 直接复用血缘登记键生成器
 * {@link ModelingLineageRegistrationService#modelAssetKey(Long)},与 {@code yak_metadata_asset} 同源。
 */
@Component
@ConditionalOnModelingPersistence
@RequiredArgsConstructor
public class ModelAssetProvider implements AssetProvider {

  private final ModelingModelMapper mapper;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.MODEL;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    List<ModelingModelPO> rows = mapper.selectList(
        new LambdaQueryWrapper<ModelingModelPO>()
            .eq(ModelingModelPO::getProjectId, query.projectId())
            .eq(ModelingModelPO::getDeleted, Boolean.FALSE)
            .gt(query.updatedAfter() != null, ModelingModelPO::getUpdateTime, query.updatedAfter())
            .gt(afterId != null, ModelingModelPO::getId, afterId)
            .orderByAsc(ModelingModelPO::getId)
            .last("LIMIT " + query.limit()));
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    List<AssetDescriptor> items = rows.stream().map(ModelAssetProvider::toDescriptor).toList();
    String nextCursor = rows.size() == query.limit()
        ? String.valueOf(rows.get(rows.size() - 1).getId())
        : null;
    return new AssetPage(items, nextCursor);
  }

  @Override
  public Optional<AssetDescriptor> refresh(String sourceId) {
    Long id = parseIdOrNull(sourceId);
    if (id == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(mapper.selectById(id))
        .filter(po -> !Boolean.TRUE.equals(po.getDeleted()))
        .map(ModelAssetProvider::toDescriptor);
  }

  static AssetDescriptor toDescriptor(ModelingModelPO po) {
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "modelCode", po.getModelCode());
    put(extra, "dialect", po.getDialect());
    put(extra, "status", po.getStatus());
    put(extra, "latestVersionNo", po.getLatestVersionNo());
    put(extra, "sourceTable", joinSource(po));
    return new AssetDescriptor(
        ModelingLineageRegistrationService.modelAssetKey(po.getId()),
        String.valueOf(po.getId()),
        po.getModelName(),
        po.getDescription(),
        AssetType.TABLE,
        po.getLayerCode(),
        null,
        po.getCreatedBy(),
        po.getUpdateTime(),
        AssetContentHash.of(
            po.getModelName(), po.getDescription(), po.getLayerCode(), po.getStatus()),
        extra);
  }

  private static String joinSource(ModelingModelPO po) {
    if (po.getSourceDatabase() == null && po.getSourceTable() == null) {
      return null;
    }
    return po.getSourceDatabase() == null
        ? po.getSourceTable()
        : po.getSourceDatabase() + "." + po.getSourceTable();
  }

  private static void put(Map<String, String> map, String key, Object value) {
    if (value != null) {
      map.put(key, String.valueOf(value));
    }
  }

  private static Long parseIdOrNull(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
