package io.yak.ops.business.job.discovery;

import io.yak.ops.business.job.task.TaskDefinition;
import io.yak.ops.business.job.task.TaskProvider;
import io.yak.ops.business.job.task.TaskRegistration;
import io.yak.ops.business.job.task.TaskRegistry;
import io.yak.ops.business.job.task.TaskVersionSnapshot;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Aggregates task registrations contributed by business-owned {@link TaskProvider}s. */
@Component
public class InMemoryTaskRegistry implements TaskRegistry {

  private final ObjectProvider<TaskProvider> taskProviderProvider;
  private final ConcurrentMap<String, TaskDefinition> tasks = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, TaskVersionSnapshot> snapshots = new ConcurrentHashMap<>();

  public InMemoryTaskRegistry(ObjectProvider<TaskProvider> taskProviderProvider) {
    this.taskProviderProvider = taskProviderProvider;
  }

  @Override
  public List<TaskDefinition> list() {
    refresh();
    return tasks.values().stream().sorted(Comparator.comparing(TaskDefinition::name)).toList();
  }

  @Override
  public TaskDefinition get(String taskId) {
    String normalized = requireTaskId(taskId);
    refresh();
    TaskDefinition task = tasks.get(normalized);
    if (task == null) throw new IllegalArgumentException("任务不存在或尚不可执行：" + taskId);
    return task;
  }

  @Override
  public TaskVersionSnapshot snapshot(String taskId) {
    String normalized = requireTaskId(taskId);
    refresh();
    TaskVersionSnapshot snapshot = snapshots.get(normalized);
    if (snapshot == null) throw new IllegalArgumentException("任务不存在或尚不可执行：" + taskId);
    return snapshot;
  }

  private String requireTaskId(String taskId) {
    if (taskId == null || taskId.isBlank()) throw new IllegalArgumentException("taskId 不能为空");
    return taskId.trim();
  }

  private void refresh() {
    Map<String, TaskDefinition> taskSnapshot = new LinkedHashMap<>();
    Map<String, TaskVersionSnapshot> versionSnapshot = new LinkedHashMap<>();

    for (TaskProvider provider : taskProviderProvider.orderedStream().toList()) {
      for (TaskRegistration registration : provider.registrations()) {
        putTask(taskSnapshot, versionSnapshot, registration);
      }
    }

    tasks.clear();
    tasks.putAll(taskSnapshot);
    snapshots.clear();
    snapshots.putAll(versionSnapshot);
  }

  private void putTask(
      Map<String, TaskDefinition> taskSnapshot,
      Map<String, TaskVersionSnapshot> versionSnapshot,
      TaskRegistration registration) {
    TaskDefinition task = registration.definition();
    TaskVersionSnapshot snapshot = registration.snapshot();
    TaskDefinition existing = taskSnapshot.putIfAbsent(task.id(), task);
    if (existing != null) {
      throw new IllegalStateException(
          "重复的工作流任务 ID：" + task.id() + "，类型=" + existing.type() + "/" + task.type());
    }
    versionSnapshot.put(task.id(), snapshot);
  }
}
