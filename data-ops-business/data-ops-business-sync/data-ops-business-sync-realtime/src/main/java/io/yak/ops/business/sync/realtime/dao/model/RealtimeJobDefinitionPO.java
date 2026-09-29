package io.yak.ops.business.sync.realtime.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Physical persistence row for RealtimeSyncTask/current Draft. */
@Data
@TableName("yak_realtime_job_definition")
public class RealtimeJobDefinitionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String jobName;
  private String description;
  private Long runtimeEnvironmentId;
  private String specJson;
  private String releaseState;
  private String desiredState;
  private String observedState;
  private Integer definitionVersion;
  private Integer publishedVersion;
  private Long publishedDefinitionVersionId;
  private String configDigest;
  private String lastError;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
