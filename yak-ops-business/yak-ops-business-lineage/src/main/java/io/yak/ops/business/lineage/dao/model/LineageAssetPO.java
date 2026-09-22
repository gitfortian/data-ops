package io.yak.ops.business.lineage.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

/** Database row for yak_metadata_asset. */
@Data
@TableName("yak_metadata_asset")
public class LineageAssetPO {

  @TableId(type = IdType.AUTO)
  private Long id;
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
  private String properties;
  private Timestamp createTime;
  private Timestamp updateTime;
}
