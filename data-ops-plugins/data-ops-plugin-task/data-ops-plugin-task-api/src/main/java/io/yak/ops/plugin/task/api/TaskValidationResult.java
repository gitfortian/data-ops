package io.yak.ops.plugin.task.api;

import java.util.Arrays;
import java.util.List;

/** Immutable validation result returned by a task plugin. */
public record TaskValidationResult(List<TaskValidationIssue> issues) {

  private static final TaskValidationResult OK = new TaskValidationResult(List.of());

  public TaskValidationResult {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public boolean valid() {
    return issues.isEmpty();
  }

  public static TaskValidationResult ok() {
    return OK;
  }

  public static TaskValidationResult invalid(TaskValidationIssue... issues) {
    if (issues == null || issues.length == 0) {
      throw new IllegalArgumentException("At least one validation issue is required");
    }
    return new TaskValidationResult(Arrays.asList(issues));
  }

  public static TaskValidationResult of(List<TaskValidationIssue> issues) {
    return new TaskValidationResult(issues);
  }
}
