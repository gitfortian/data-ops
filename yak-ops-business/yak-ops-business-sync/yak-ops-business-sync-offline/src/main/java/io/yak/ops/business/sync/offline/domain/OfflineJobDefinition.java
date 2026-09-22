package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 离线同步任务定义领域模型。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineJobDefinition {
  private Long id;
  private Long projectId;
  private String jobName;
  private String jobDesc;
  private String mode;
  private String definitionJson;
  private String jobSpecJson;
  private String configDigest;
  private String releaseState;
  private String sourceType;
  private String sinkType;
  private Long sourceDatasourceId;
  private Long sinkDatasourceId;
  private String sourceDatasourceName;
  private String sinkDatasourceName;
  private String sourceTable;
  private String sinkTable;
  private String scheduleJson;
  private Boolean scheduleEnabled;
  private String cronExpression;
  private Integer retryMaxAttempts;
  private Integer retryBackoffSeconds;
  private String notificationConfigJson;
  private String editorMetaJson;
  private LocalDateTime scheduleLastFireTime;
  private LocalDateTime scheduleNextFireTime;
  private Integer version;
  /** 当前生效的发布版本 ID；NULL=从未发布。草稿列与它无关（契约 C4）。 */
  private Long publishedRevisionId;
  private Integer latestVersionNo;
  private Long lastExecutionId;
  private String lastEngineJobId;
  private String lastJobStatus;
  private String lastErrorMessage;
  private Long lastDurationMillis;
  private Long lastReadRowCount;
  private Double lastQps;
  private Long lastSyncBytes;
  private LocalDateTime lastStartTime;
  private LocalDateTime lastEndTime;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** Definition is the Offline Sync Project source truth. */
  public long requireProjectId() {
    if (projectId == null || projectId <= 0L) {
      throw new IllegalStateException("离线同步任务缺少有效的 Project Space 归属：" + id);
    }
    return projectId;
  }
}
