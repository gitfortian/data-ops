package io.yak.ops.business.development.task;

import io.yak.ops.plugin.task.api.TaskValidationIssue;
import java.util.List;

/** Internal validation decision kept separate from the legacy application exception type. */
public record DevelopmentTaskValidation(
    boolean valid,
    String message,
    List<TaskValidationIssue> issues) {

  public DevelopmentTaskValidation {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public static DevelopmentTaskValidation ok() {
    return new DevelopmentTaskValidation(true, null, List.of());
  }

  public static DevelopmentTaskValidation invalid(
      String message,
      List<TaskValidationIssue> issues) {
    return new DevelopmentTaskValidation(false, message, issues);
  }
}
