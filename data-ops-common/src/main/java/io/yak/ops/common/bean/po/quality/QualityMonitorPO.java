package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_monitor")
public class QualityMonitorPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String monitorName;
  private String description;
  private Long dataSourceId;
  private String dataSourceName;
  private String databaseName;
  private String schemaName;
  private String tableName;
  private String whereClause;
  private String owner;
  private Boolean enabled;
  private String lastResult;
  private String lastExecutionNo;
  private LocalDateTime lastRunTime;
  private Boolean deleted;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
