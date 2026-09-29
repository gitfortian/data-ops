package io.yak.ops.business.dashboard.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.dashboard.dao.mapper.DashboardMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import io.yak.ops.business.dashboard.lineage.DashboardLineageSynchronizer;
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
 * DASHBOARD 资产供给(asset ticket 97):只读本域 yak_dashboard,asset_key 复用血缘登记键
 * {@link DashboardLineageSynchronizer#dashboardAssetKey(long)}(D6 同源)。
 * 本域无负责人/状态列,suggestedOwner 留空由编目规则或人工补齐。
 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DashboardAssetProvider implements AssetProvider {

  private final DashboardMapper mapper;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.DASHBOARD;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    List<DashboardPO> rows = mapper.selectList(
        new LambdaQueryWrapper<DashboardPO>()
            .eq(DashboardPO::getProjectId, query.projectId())
            .gt(query.updatedAfter() != null, DashboardPO::getUpdateTime,
                query.updatedAfter() == null ? null : Timestamp.valueOf(query.updatedAfter()))
            .gt(afterId != null, DashboardPO::getId, afterId)
            .orderByAsc(DashboardPO::getId)
            .last("LIMIT " + query.limit()));
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    List<AssetDescriptor> items = rows.stream().map(DashboardAssetProvider::toDescriptor).toList();
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
    return Optional.ofNullable(mapper.selectById(id)).map(DashboardAssetProvider::toDescriptor);
  }

  static AssetDescriptor toDescriptor(DashboardPO po) {
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "currentVersionNo", po.getCurrentVersionNo());
    put(extra, "publishedVersionNo", po.getPublishedVersionNo());
    return new AssetDescriptor(
        DashboardLineageSynchronizer.dashboardAssetKey(po.getId()),
        String.valueOf(po.getId()),
        po.getName(),
        po.getDescription(),
        AssetType.DASHBOARD,
        null,
        null,
        null,
        po.getUpdateTime() == null ? null : po.getUpdateTime().toLocalDateTime(),
        AssetContentHash.of(po.getName(), po.getDescription(),
            String.valueOf(po.getCurrentVersionNo()), String.valueOf(po.getPublishedVersionNo())),
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
