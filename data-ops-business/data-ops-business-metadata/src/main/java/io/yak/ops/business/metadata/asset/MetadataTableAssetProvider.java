package io.yak.ops.business.metadata.asset;

import io.yak.ops.business.asset.api.AssetContentHash;
import io.yak.ops.business.asset.api.AssetCursorQuery;
import io.yak.ops.business.asset.api.AssetDescriptor;
import io.yak.ops.business.asset.api.AssetPage;
import io.yak.ops.business.asset.api.AssetProvider;
import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import io.yak.ops.business.metadata.dao.mapper.CatalogTableAssetProviderMapper;
import io.yak.ops.business.metadata.dao.model.CatalogTableAssetRow;
import io.yak.ops.common.enums.asset.AssetEnums.AssetType;
import io.yak.ops.common.enums.asset.AssetSourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * METADATA 资产供给（M2-2 B「直挂表」搭桥）：把采集通道落在 {@code yak_metadata_asset} 里的
 * 物理表行供给资产台账，让状态机 7 格的「已发现」对真实物理表成立。
 *
 * <p>assetKey 是 {@code PhysicalTableAssetKey} 归一键的<b>逐字符直通</b>（D6）——本行的
 * {@code asset_key} 列与血缘登记同源，不做任何二次加工。sourceId 用本表主键
 * （表列 {@code source_id} 是数据源 id，一行一源，不能当资产身份）。
 *
 * <p>domainCode 首版留空：{@code domain_ids} 存的是语义域 id 列表，解析成 code 需要
 * semantic 依赖，按 metric 先例降级（解析失败不影响指纹，避免依赖抖动误报 META_CHANGED）。
 * suggestedOwner 直通 {@code owner_user}：采集行该列恒为 NULL（登记通道才写归属），
 * 留空就是"没人认领"的诚实表达，入台账后由指派规则补。
 */
@Component
@ConditionalOnMetadataPersistence
@RequiredArgsConstructor
public class MetadataTableAssetProvider implements AssetProvider {

  private final CatalogTableAssetProviderMapper mapper;

  @Override
  public AssetSourceType sourceType() {
    return AssetSourceType.METADATA;
  }

  @Override
  public AssetPage cursorList(AssetCursorQuery query) {
    Long afterId = parseIdOrNull(query.cursor());
    List<CatalogTableAssetRow> rows =
        mapper.selectProviderPage(query.projectId(), query.updatedAfter(), afterId, query.limit());
    if (rows.isEmpty()) {
      return AssetPage.empty();
    }
    List<AssetDescriptor> items = rows.stream().map(MetadataTableAssetProvider::toDescriptor).toList();
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
    return Optional.ofNullable(mapper.selectProviderRowById(id))
        .map(MetadataTableAssetProvider::toDescriptor);
  }

  static AssetDescriptor toDescriptor(CatalogTableAssetRow row) {
    String name = StringUtils.hasText(row.getDisplayName()) ? row.getDisplayName() : row.getName();
    Map<String, String> extra = new LinkedHashMap<>();
    put(extra, "dataSourceId", row.getDataSourceId());
    put(extra, "databaseName", row.getDatabaseName());
    put(extra, "schemaName", row.getSchemaName());
    put(extra, "tableName", row.getTableName());
    put(extra, "entityStatus", row.getEntityStatus());
    return new AssetDescriptor(
        row.getAssetKey(),
        String.valueOf(row.getId()),
        name,
        row.getSummary(),
        AssetType.TABLE,
        row.getLayerCode(),
        null,
        row.getOwnerUser(),
        row.getUpdateTime(),
        AssetContentHash.of(
            name, row.getSummary(), row.getLayerCode(), row.getEntityStatus()),
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
