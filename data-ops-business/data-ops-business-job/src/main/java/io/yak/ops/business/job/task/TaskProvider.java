package io.yak.ops.business.job.task;

import java.util.List;

/** Supplies workflow-visible tasks without making the Registry depend on concrete business domains. */
public interface TaskProvider {

  List<TaskDefinition> list();

  TaskVersionSnapshot snapshot(String taskId);

  /**
   * Supplies descriptor + immutable snapshot as one consistent registration.
   *
   * <p>Existing providers remain source-compatible. Null/incomplete descriptors keep the previous
   * registry behavior and are ignored; a real descriptor/snapshot mismatch fails explicitly.</p>
   */
  default List<TaskRegistration> registrations() {
    return list().stream()
        .filter(task -> task != null && task.id() != null && !task.id().isBlank())
        .map(task -> new TaskRegistration(task, snapshot(task.id())))
        .toList();
  }
}
