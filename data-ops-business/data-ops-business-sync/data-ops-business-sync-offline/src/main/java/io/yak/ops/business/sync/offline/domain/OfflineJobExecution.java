package io.yak.ops.business.sync.offline.domain;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ExecutionAttempt 的持久化兼容视图。
 *
 * <p>约束：新运行链必须绑定 {@code batchId}；{@code batchId == null} 仅存在于该约束生效前的历史记录，
 * 只能查询，不参与 Retry、Cancel、Reconcile 或 Task runtime projection。
 *
 * <p>{@code definitionVersion/configDigest/definitionSnapshotJson/submittedConfig} 继续写入是为了兼容既有表结构、
 * 历史接口与审计，不是新的运行真相。冻结执行证据只从 BatchExecution.ExecutionSnapshot 读取。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineJobExecution {
  private Long id;
  private Long jobDefinitionId;
  private Long batchId;
  private Integer definitionVersion;
  private String engineBaseUrl;
  private String engineJobId;
  private String externalExecutionId;
  private String idempotencyKey;
  private String workerInstanceId;
  private String status;
  private Long stateVersion;
  private Integer attemptNo;
  private String triggerType;
  private Long retryFromExecutionId;
  private Boolean cancellationRequested;
  private Boolean retryCreated;
  private LocalDateTime nextRetryTime;
  private String configDigest;
  private String definitionSnapshotJson;
  private String submittedConfig;
  private String engineSnapshotJson;
  private String errorMessage;
  private Long sourceRecordCount;
  private Long sinkAttemptedRecordCount;
  private Long sinkSuccessRecordCount;
  private Long sinkCommittedRecordCount;
  private Long sourceReadBytes;
  private Long sinkWrittenBytes;
  private Double sourceAverageQps;
  private Double sinkAverageQps;
  private Long failedRecordCount;
  private Long skippedRecordCount;
  private Long databaseCommitMillis;
  private Long sqlExecutionMillis;
  private Double qps;
  private Long durationMillis;
  private LocalDateTime createTime;
  private LocalDateTime startTime;
  private LocalDateTime endTime;
  private LocalDateTime lastSyncTime;
  private LocalDateTime updateTime;
}
