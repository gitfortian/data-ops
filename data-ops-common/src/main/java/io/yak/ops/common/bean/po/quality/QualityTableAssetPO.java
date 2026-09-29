package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_table_asset")
public class QualityTableAssetPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long dataSourceId;
  private String dataSourceName;
  private String databaseName;
  private String schemaName;
  private String tableName;
  private String tableType;
  private String remarks;
  private String registeredBy;
  private LocalDateTime registeredAt;
  private Boolean deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
