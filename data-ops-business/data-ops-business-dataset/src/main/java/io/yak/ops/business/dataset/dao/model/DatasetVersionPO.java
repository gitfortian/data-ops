package io.yak.ops.business.dataset.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

/** yak_dataset_version table row. JSON columns remain raw strings at the persistence boundary. */
@Data
@TableName("yak_dataset_version")
public class DatasetVersionPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long datasetId;
  private Integer versionNo;
  private String sourceType;
  private Long sourceTaskAssetId;
  private Long sourceTaskRevisionId;
  private Integer sourceTaskRevisionNo;
  private String dataSourceId;
  private String sqlContent;
  private String schemaSnapshot;
  private Timestamp createTime;
}
