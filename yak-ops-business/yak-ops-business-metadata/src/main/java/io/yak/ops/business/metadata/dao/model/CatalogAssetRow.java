package io.yak.ops.business.metadata.dao.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * {@code yak_metadata_asset} 上<b>目录侧</b>写路径用到的一行。
 *
 * <p>不复用 lineage 的 {@code LineageAssetPO}（那是对方模块的内部模型，且只映射 lineage 的 17 列，
 * 读目录列会直接丢字段）。共表两侧各有各的行模型，共同的只有那把 {@code uk (project_scope_id, asset_key)}。
 *
 * <p>字段分两组：{@link #projectId} 到 {@link #columnName} 是 lineage owns 的列，<b>只在 INSERT 时给值</b>；
 * {@link #typeId} 往后是目录 owns 的列，每轮刷新。分界原因见模块 ARCHITECTURE.md 的 steward 契约。
 */
@Data
public class CatalogAssetRow {

  // —— lineage 拥有：INSERT 写一次，UPDATE 子句里不出现 ——
  private Long projectId;
  private String assetKey;
  private String assetType;
  private String name;
  private String sourceType;
  private String sourceId;
  private Long parentAssetId;
  private String dataSourceId;
  private String databaseName;
  private String schemaName;
  private String tableName;
  private String columnName;

  // —— metadata 拥有 ——
  private Long typeId;
  private String displayName;
  private String fullyQualifiedName;
  private String fqnHash;
  private String summary;
  private String entityStatus;
  /** 投影归属三列（ticket 130）：只有登记/投影通道给值，采集通道永远为 NULL。 */
  private String ownerUser;
  private String domainIds;
  private String layerCode;
  private String providerType;
  private Long collectJobId;
  private String contentHash;
  private String sourceHash;
  private LocalDateTime sourceUpdatedAt;
  private LocalDateTime firstSeenAt;
  private LocalDateTime lastCollectAt;
  private String mdAttributes;
  private String updatedBy;
}
