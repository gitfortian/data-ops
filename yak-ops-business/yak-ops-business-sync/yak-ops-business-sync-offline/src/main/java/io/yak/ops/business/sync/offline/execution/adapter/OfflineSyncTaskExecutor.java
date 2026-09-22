package io.yak.ops.business.sync.offline.execution.adapter;

import io.yak.ops.business.job.task.TaskExecution;
import io.yak.ops.business.job.task.TaskExecutor;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionDetailVO;
import io.yak.ops.common.bean.vo.sync.offline.OfflineJobExecutionVO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Adapts the Offline Sync-owned execution lifecycle to the generic Job task runtime contract. */
@Component
public class OfflineSyncTaskExecutor implements TaskExecutor {

  private final ObjectProvider<OfflineJobExecutionService> executionServiceProvider;

  public OfflineSyncTaskExecutor(
      ObjectProvider<OfflineJobExecutionService> executionServiceProvider) {
    this.executionServiceProvider = executionServiceProvider;
  }

  @Override
  public String taskType() {
    return "SYNC";
  }

  @Override
  public TaskExecution start(
      TaskVersionSnapshot snapshot,
      String idempotencyKey,
      Map<String, Object> input) {
    if (snapshot == null) throw new IllegalArgumentException("任务版本快照不能为空");
    if (!"SYNC".equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException("仅支持 SYNC 任务版本快照：" + snapshot.taskId());
    }

    OfflineJobExecutionVO execution;
    if (snapshot.version() <= 0L
        || snapshot.definitionSnapshotJson() == null
        || snapshot.executionConfigSnapshotJson() == null) {
      execution = service().execute(parseId(snapshot.taskId(), "taskId"));
    } else {
      execution = service().executeSnapshot(
          parseId(snapshot.taskId(), "taskId"),
          snapshot.version(),
          snapshot.configDigest(),
          snapshot.definitionSnapshotJson(),
          snapshot.executionConfigSnapshotJson(),
          idempotencyKey);
    }
    return toExecution(execution);
  }

  @Override
  public TaskExecution status(String executionId) {
    OfflineJobExecutionDetailVO detail = service().detail(parseId(executionId, "executionId"));
    OfflineJobExecutionVO execution = detail.getSummary() != null
        ? detail.getSummary()
        : detail.getExecution();
    if (execution == null) {
      throw new IllegalStateException("同步任务执行详情为空：" + executionId);
    }
    return toExecution(execution);
  }

  @Override
  public void cancel(String executionId) {
    service().cancel(parseId(executionId, "executionId"));
  }

  private OfflineJobExecutionService service() {
    OfflineJobExecutionService service = executionServiceProvider.getIfAvailable();
    if (service == null) {
      throw new IllegalStateException("离线同步能力未启用，无法执行 SYNC 任务");
    }
    return service;
  }

  private TaskExecution toExecution(OfflineJobExecutionVO execution) {
    Map<String, Object> output = new LinkedHashMap<>();
    put(output, "engineJobId", execution.getEngineJobId());
    put(output, "externalExecutionId", execution.getExternalExecutionId());
    put(output, "sourceRecordCount", execution.getSourceRecordCount());
    put(output, "sinkCommittedRecordCount", execution.getSinkCommittedRecordCount());
    put(output, "failedRecordCount", execution.getFailedRecordCount());
    put(output, "durationMillis", execution.getDurationMillis());
    return new TaskExecution(
        String.valueOf(execution.getId()),
        execution.getStatus(),
        execution.getErrorMessage(),
        output);
  }

  private void put(Map<String, Object> target, String key, Object value) {
    if (value != null) target.put(key, value);
  }

  private Long parseId(String value, String name) {
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0L) throw new NumberFormatException(value);
      return parsed;
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException(name + " 不合法：" + value, exception);
    }
  }
}
