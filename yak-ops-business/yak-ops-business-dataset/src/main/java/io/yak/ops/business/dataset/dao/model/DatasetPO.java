package io.yak.ops.business.dataset.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

/** yak_dataset table row. */
@Data
@TableName("yak_dataset")
public class DatasetPO {

  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long developmentNodeId;
  private String name;
  private String description;
  private String status;
  private Long currentVersionId;
  private String draftDataSourceId;
  private String draftSql;
  private String draftSchemaSnapshot;
  private Timestamp createTime;
  private Timestamp updateTime;
}
