package io.yak.ops.business.metadata.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 目录行（{@code yak_metadata_asset}）读路径的<b>唯一出处</b>：列清单、槽位列、行映射三样只定义一次。
 *
 * <p>统一检索与详情聚合读的是同一张表的同一批列，两套行映射必然会漂移成"搜索里有的字段详情里没有"。
 * {@code MetadataQueryApi} 的便捷方法（子级列 / 物理列 / 按键找表）因此只是换过滤条件，不换读法。
 *
 * <p>共表契约（plan §2.3 后果 2）：属性袋只读 {@code md_attributes}，永不读 lineage 的 {@code properties}。
 */
public final class CatalogRowRead {

  /** 目录固有列 + 属性袋；{@code a.} 是调用方给表起的别名。 */
  public static final String SELECT_COLUMNS =
      """
      a.id, a.asset_key AS assetKey, a.name, a.display_name AS displayName,
      a.summary, a.type_id AS typeId, a.parent_asset_id AS parentAssetId,
      a.provider_type AS providerType,
      -- 源侧坐标只读回：投影实体的「跳转源域编辑」要用 source_id 当目标；共表契约限的是写，不是读。
      a.source_type AS sourceType, a.source_id AS sourceId,
      a.collect_job_id AS collectJobId,
      a.entity_status AS entityStatus, a.owner_user AS ownerUser,
      a.domain_ids AS domainIds, a.layer_code AS layerCode,
      a.data_source_id AS dataSourceId, a.database_name AS databaseName,
      a.schema_name AS schemaName, a.table_name AS tableName, a.column_name AS columnName,
      a.fully_qualified_name AS fullyQualifiedName,
      a.content_hash AS contentHash, a.source_hash AS sourceHash,
      a.source_updated_at AS sourceUpdatedAt, a.first_seen_at AS firstSeenAt,
      a.last_collect_at AS lastCollectAt, a.last_change_at AS lastChangeAt,
      a.catalog_version AS catalogVersion, a.updated_by AS updatedBy,
      a.create_time AS createTime, a.update_time AS updateTime,
      a.md_attributes AS attributesRaw
      """;

  private CatalogRowRead() {}

  /** 一行目录 → 有序 Map。 */
  public static Map<String, Object> mapRow(ResultSet rs, ObjectMapper objectMapper) throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", rs.getLong("id"));
    row.put("assetKey", rs.getString("assetKey"));
    row.put("name", rs.getString("name"));
    row.put("displayName", rs.getString("displayName"));
    row.put("summary", rs.getString("summary"));
    row.put("typeId", normalize(rs.getObject("typeId")));
    row.put("parentAssetId", normalize(rs.getObject("parentAssetId")));
    row.put("providerType", rs.getString("providerType"));
    row.put("sourceType", rs.getString("sourceType"));
    row.put("sourceId", rs.getString("sourceId"));
    row.put("collectJobId", normalize(rs.getObject("collectJobId")));
    row.put("entityStatus", rs.getString("entityStatus"));
    row.put("ownerUser", rs.getString("ownerUser"));
    row.put("domainIds", splitCsv(rs.getString("domainIds")));
    row.put("layerCode", rs.getString("layerCode"));
    row.put("dataSourceId", normalize(rs.getObject("dataSourceId")));
    row.put("databaseName", rs.getString("databaseName"));
    row.put("schemaName", rs.getString("schemaName"));
    row.put("tableName", rs.getString("tableName"));
    row.put("columnName", rs.getString("columnName"));
    row.put("fullyQualifiedName", rs.getString("fullyQualifiedName"));
    row.put("contentHash", rs.getString("contentHash"));
    row.put("sourceHash", rs.getString("sourceHash"));
    row.put("sourceUpdatedAt", normalize(rs.getObject("sourceUpdatedAt")));
    row.put("firstSeenAt", normalize(rs.getObject("firstSeenAt")));
    row.put("lastCollectAt", normalize(rs.getObject("lastCollectAt")));
    row.put("lastChangeAt", normalize(rs.getObject("lastChangeAt")));
    row.put("catalogVersion", normalize(rs.getObject("catalogVersion")));
    row.put("updatedBy", rs.getString("updatedBy"));
    row.put("createTime", normalize(rs.getObject("createTime")));
    row.put("updateTime", normalize(rs.getObject("updateTime")));
    row.put("attributes", parseAttributes(objectMapper, rs.getString("attributesRaw")));
    return row;
  }

  /** 目录的属性袋（steward 契约：只读 md_attributes，永不碰 lineage 的 properties）。 */
  private static Object parseAttributes(ObjectMapper objectMapper, String raw) {
    if (raw == null || raw.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(raw, Object.class);
    } catch (Exception e) {
      // JSON 列理论上不会给出坏值；真给出来了就原样带回，让"展示面坏数据"可见而不是静默吞掉。
      return raw;
    }
  }

  private static List<String> splitCsv(String csv) {
    if (csv == null || csv.isBlank()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String part : csv.split(",")) {
      if (!part.isBlank()) {
        out.add(part.trim());
      }
    }
    return List.copyOf(out);
  }

  /** MySQL 驱动给 DATETIME 的是 Timestamp，进 JSON 会变成 epoch 数字——统一还原为 LocalDateTime。 */
  public static Object normalize(Object value) {
    if (value instanceof Timestamp timestamp) {
      return timestamp.toLocalDateTime();
    }
    return value;
  }
}
