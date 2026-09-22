package io.yak.ops.business.development.asset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.development.dao.mapper.DevelopmentNodeMapper;
import io.yak.ops.business.development.service.DevelopmentSqlLineageService;
import io.yak.ops.common.bean.po.development.DevelopmentNodePO;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * TASK 资产供给(asset ticket 97):数据开发 SQL 任务节点(yak_dev_node, type=SQL)。
 * asset_key 复用血缘登记键 {@link DevelopmentSqlLineageService#sqlTaskAssetKey(long)}(D6 同源);
 * 未发布的 SQL 节点暂无血缘记录,详情页血缘分区按 UNAVAILABLE 降级。
 */
@Component
@ConditionalOnDataSourceEnabled
public class TaskAssetProvider implements AssetProvider {

  private final DevelopmentNodeMapper mapper;

  public TaskAssetProvider(DevelopmentNodeMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.TASK;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    // @TableLogic deleted 由 MyBatis-Plus 自动过滤
    List<DevelopmentNodePO> rows = mapper.selectList(
        new LambdaQueryWrapper<DevelopmentNodePO>()
            .eq(DevelopmentNodePO::getProjectId, query.projectId())
            .apply("LOWER(type) = 'sql'")
            .gt(query.updatedAfter() != null, DevelopmentNodePO::getUpdateTime,
                toInstant(query.updatedAfter()))
            .gt(afterId != null, DevelopmentNodePO::getId, afterId)
            .orderByAsc(DevelopmentNodePO::getId)
            .last("LIMIT " + query.limit()));
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    List<AssetDescriptor> items = rows.stream().map(TaskAssetProvider::toDescriptor).toList();
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
    return Optional.ofNullable(mapper.selectById(id)).map(TaskAssetProvider::toDescriptor);
  }

  static AssetDescriptor toDescriptor(DevelopmentNodePO po) {
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "type", po.getType());
    put(extra, "configured", po.getConfigured());
    put(extra, "directoryId", po.getDirectoryId());
    return new AssetDescriptor(
        DevelopmentSqlLineageService.sqlTaskAssetKey(po.getId()),
        String.valueOf(po.getId()),
        po.getName(),
        null,
        AssetType.TASK,
        null,
        null,
        po.getUpdatedBy(),
        toLocalDateTime(po.getUpdateTime()),
        AssetContentHash.of(po.getName(), po.getType(), String.valueOf(po.getConfigured())),
        extra);
  }

  private static void put(Map<String, String> map, String key, Object value) {
    if (value != null) {
      map.put(key, String.valueOf(value));
    }
  }

  private static Instant toInstant(LocalDateTime value) {
    return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
  }

  private static LocalDateTime toLocalDateTime(Instant value) {
    return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
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
