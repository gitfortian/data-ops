package io.yak.ops.business.sync.realtime.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** Physical persistence row for a realtime SyncExecution. */
@Data
@TableName("yak_realtime_job_deployment")
public class RealtimeJobDeploymentPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private Long definitionId;
  private Long definitionVersionId;
  private Integer definitionVersion;
  private Long runtimeEnvironmentId;
  private Integer runtimeEnvironmentVersion;
  private String runtimeEnvironmentSnapshotJson;
  private String specSnapshotJson;
  private String specSummary;
  private String configDigest;
  private String idempotencyKey;
  private String engineType;
  private String desiredState;
  private String observedState;
  private String runtimeJobName;
  private String runtimeIdentityState;
  private String gatewayJobId;
  private String runtimeVersion;
  private String runtimeRevision;
  private String status;
  private Boolean resultUncertain;
  private String errorMessage;
  private String replacementCommandType;
  private Long replacementTargetDefinitionVersionId;
  private String replacementIdempotencyKey;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
