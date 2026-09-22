package io.yak.ops.business.job.task;

import java.util.List;

/** 工作流任务发现边界。 */
public interface TaskRegistry {

  List<TaskDefinition> list();

  TaskDefinition get(String taskId);

  /**
   * 获取用于工作流发布/测试运行的不可变任务配置快照。
   *
   * <p>没有版本能力的任务类型可继续使用默认实现；SYNC 注册表会覆盖该方法并返回真实版本快照。</p>
   */
  default TaskVersionSnapshot snapshot(String taskId) {
    return TaskVersionSnapshot.current(get(taskId));
  }
}
