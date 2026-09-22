package io.yak.ops.business.workflow.definition;

import io.yak.ops.business.taskcatalog.domain.TaskAsset;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.util.Locale;
import java.util.Set;

/** Fail-closed orchestration policy for published Data Development task assets. */
final class WorkflowTaskEligibilityPolicy {

  private static final Set<String> DATA_DEVELOPMENT_TASK_TYPES = Set.of(
      "SQL",
      "SHELL",
      "HTTP",
      "PYTHON");

  private WorkflowTaskEligibilityPolicy() {}

  static void requireEligible(TaskAsset asset) {
    if (asset == null) throw new IllegalArgumentException("工作流任务资产不能为空");
    if (asset.source() != TaskAssetSource.DATA_DEVELOPMENT) return;

    String taskType = normalize(asset.taskType());
    if (!DATA_DEVELOPMENT_TASK_TYPES.contains(taskType)) {
      throw new IllegalArgumentException(
          "数据开发资产不能进入工作流编排：assetId=" + asset.id() + "，taskType=" + taskType);
    }
  }

  static boolean supportsDataDevelopmentTaskType(String taskType) {
    return DATA_DEVELOPMENT_TASK_TYPES.contains(normalize(taskType));
  }

  private static String normalize(String taskType) {
    return taskType == null ? "" : taskType.trim().toUpperCase(Locale.ROOT);
  }
}
