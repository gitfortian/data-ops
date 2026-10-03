package io.yak.ops.business.lifecycle.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 每日存储快照(SHOW DATA 采集)。 */
@Data
@TableName("yak_lc_storage_snapshot")
public class LifecycleStorageSnapshotPO {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long projectId;
  private LocalDate snapshotDate;
  private String layerCode;
  private Long datasourceId;
  private String databaseName;
  private String tableName;
  private Long sizeBytes;
  private LocalDateTime createTime;
}
