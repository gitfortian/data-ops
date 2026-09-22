package io.yak.ops.business.analysis.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.analysis.dao.mapper.AnalysisMapper;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import io.yak.ops.business.analysis.lineage.AnalysisLineageSynchronizer;
import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
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
 * CHART 资产供给(asset ticket 97):可复用分析(yak_analysis)→ chart:analysis:{id},
 * 键复用血缘生成器 {@link AnalysisLineageSynchronizer#chartAssetKey(long)}(D6 同源)。
 * 仪表板内嵌小图(chart:dashboard:…:widget:…)不是独立资产,不进台账。
 */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class ChartAssetProvider implements AssetProvider {

  private final AnalysisMapper mapper;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.CHART;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    List<AnalysisPO> rows = mapper.selectList(
        new LambdaQueryWrapper<AnalysisPO>()
            .eq(AnalysisPO::getProjectId, query.projectId())
            .gt(query.updatedAfter() != null, AnalysisPO::getUpdateTime,
                query.updatedAfter() == null ? null : Timestamp.valueOf(query.updatedAfter()))
            .gt(afterId != null, AnalysisPO::getId, afterId)
            .orderByAsc(AnalysisPO::getId)
            .last("LIMIT " + query.limit()));
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    List<AssetDescriptor> items = rows.stream().map(ChartAssetProvider::toDescriptor).toList();
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
    return Optional.ofNullable(mapper.selectById(id)).map(ChartAssetProvider::toDescriptor);
  }

  static AssetDescriptor toDescriptor(AnalysisPO po) {
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "chartType", po.getChartType());
    put(extra, "datasetId", po.getDatasetId());
    return new AssetDescriptor(
        AnalysisLineageSynchronizer.chartAssetKey(po.getId()),
        String.valueOf(po.getId()),
        po.getName(),
        po.getDescription(),
        AssetType.CHART,
        null,
        null,
        null,
        po.getUpdateTime() == null ? null : po.getUpdateTime().toLocalDateTime(),
        AssetContentHash.of(po.getName(), po.getDescription(), po.getChartType(),
            String.valueOf(po.getDatasetId())),
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
