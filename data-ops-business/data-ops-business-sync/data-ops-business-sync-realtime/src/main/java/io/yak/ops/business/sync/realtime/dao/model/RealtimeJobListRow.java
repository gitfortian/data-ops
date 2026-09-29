package io.yak.ops.business.sync.realtime.dao.model;

import java.time.LocalDateTime;
import lombok.Data;

/** DAO-local joined row for the list page. */
@Data
public class RealtimeJobListRow {
  private Long id;
  private String jobName;
  private String description;
  private Long runtimeEnvironmentId;
  private String specJson;
  private String releaseState;
  private String desiredState;
  private String observedState;
  private Integer definitionVersion;
  private Integer publishedVersion;
  private Boolean publishedUpdateAvailable;
  private String configDigest;
  private String lastError;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  private Long deploymentId;
  private Integer deploymentDefinitionVersion;
  private String deploymentSpecSummary;
  private String deploymentConfigDigest;
  private String deploymentIdempotencyKey;
  private String deploymentGatewayJobId;
  private String deploymentRuntimeRevision;
  private String deploymentRuntimeEnvironmentSnapshotJson;
  private String deploymentStatus;
  private Boolean deploymentResultUncertain;
  private String deploymentErrorMessage;
  private LocalDateTime deploymentCreateTime;
  private LocalDateTime deploymentUpdateTime;
}
