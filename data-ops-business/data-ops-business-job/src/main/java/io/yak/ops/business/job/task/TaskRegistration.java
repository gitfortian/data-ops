package io.yak.ops.business.job.task;

/** One discoverable task paired with the immutable snapshot that will be executed. */
public record TaskRegistration(
    TaskDefinition definition,
    TaskVersionSnapshot snapshot) {

  public TaskRegistration {
    if (definition == null) throw new IllegalArgumentException("task definition 不能为空");
    if (snapshot == null) throw new IllegalArgumentException("task snapshot 不能为空");
    if (definition.id() == null || definition.id().isBlank()) {
      throw new IllegalArgumentException("task definition id 不能为空");
    }
    if (definition.type() == null || definition.type().isBlank()) {
      throw new IllegalArgumentException("task definition type 不能为空");
    }
    if (!definition.id().equals(snapshot.taskId())) {
      throw new IllegalArgumentException("任务定义与版本快照 ID 不匹配：" + definition.id());
    }
    if (!definition.type().equalsIgnoreCase(snapshot.type())) {
      throw new IllegalArgumentException("任务定义与版本快照类型不匹配：" + definition.id());
    }
  }
}
