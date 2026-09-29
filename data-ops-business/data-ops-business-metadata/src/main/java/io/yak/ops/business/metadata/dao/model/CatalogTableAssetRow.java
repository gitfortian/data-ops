package io.yak.ops.business.metadata.dao.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 资产供给（METADATA provider，M2-2 B）读取的表级行。
 *
 * <p>与 {@link CatalogAssetRow}（写路径）分开建模：provider 只读台账比对要用的列，
 * 不携带 lineage owns 的可写字段，误用无从谈起。
 */
@Data
public class CatalogTableAssetRow {

  private Long id;
  private String assetKey;
  private String name;
  private String displayName;
  private String summary;
  private String layerCode;
  private String ownerUser;
  private String entityStatus;
  private String dataSourceId;
  private String databaseName;
  private String schemaName;
  private String tableName;
  private LocalDateTime updateTime;
}
