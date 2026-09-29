package io.yak.ops.business.job.task;

/**
 * 工作流发布时固定的任务版本快照。
 *
 * <p>工作流运行只依赖这份不可变快照，不再回读任务当前配置，确保已发布工作流可复现。</p>
 */
public record TaskVersionSnapshot(
    String taskId,
    String name,
    String type,
    long version,
    String configDigest,
    String definitionSnapshotJson,
    String executionConfigSnapshotJson) {

  public TaskVersionSnapshot {
    if (taskId == null || taskId.isBlank()) {
      throw new IllegalArgumentException("taskId 不能为空");
    }
    name = name == null || name.isBlank() ? taskId : name;
    type = type == null || type.isBlank() ? "UNKNOWN" : type;
    version = Math.max(0L, version);
  }

  public static TaskVersionSnapshot current(TaskDefinition definition) {
    return new TaskVersionSnapshot(
        definition.id(),
        definition.name(),
        definition.type(),
        0L,
        null,
        null,
        null);
  }
}
