package io.yak.ops.business.dataset.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.dataset.dao.mapper.DatasetMapper;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import io.yak.ops.business.dataset.lineage.DatasetLineageSynchronizer;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * DATASET 资产供给(asset ticket 97):只读本域 yak_dataset,asset_key 复用血缘登记键生成器
 * {@link DatasetLineageSynchronizer#datasetAssetKey(long)}(D6 同源,不造第二套键)。
 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DatasetAssetProvider implements AssetProvider {

  private final DatasetMapper mapper;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.DATASET;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    List<DatasetPO> rows = mapper.selectList(
        new LambdaQueryWrapper<DatasetPO>()
            .eq(DatasetPO::getProjectId, query.projectId())
            .gt(query.updatedAfter() != null, DatasetPO::getUpdateTime,
                query.updatedAfter() == null ? null : Timestamp.valueOf(query.updatedAfter()))
            .gt(afterId != null, DatasetPO::getId, afterId)
            .orderByAsc(DatasetPO::getId)
            .last("LIMIT " + query.limit()));
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    List<AssetDescriptor> items = rows.stream().map(DatasetAssetProvider::toDescriptor).toList();
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
    return Optional.ofNullable(mapper.selectById(id)).map(DatasetAssetProvider::toDescriptor);
  }

  static AssetDescriptor toDescriptor(DatasetPO po) {
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "status", po.getStatus());
    put(extra, "currentVersionId", po.getCurrentVersionId());
    put(extra, "developmentNodeId", po.getDevelopmentNodeId());
    return new AssetDescriptor(
        DatasetLineageSynchronizer.datasetAssetKey(po.getId()),
        String.valueOf(po.getId()),
        po.getName(),
        po.getDescription(),
        AssetType.DATASET,
        null,
        null,
        null,
        po.getUpdateTime() == null ? null : po.getUpdateTime().toLocalDateTime(),
        AssetContentHash.of(po.getName(), po.getDescription(), po.getStatus(),
            String.valueOf(po.getCurrentVersionId())),
        extra);
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
