package io.yak.ops.business.development.service;

import io.yak.ops.plugin.task.api.TaskValidationIssue;
import java.util.List;

/** Structured publish-time validation failure returned by a TaskPlugin. */
public class DevelopmentTaskValidationException extends RuntimeException {

  private final List<TaskValidationIssue> issues;

  public DevelopmentTaskValidationException(String message, List<TaskValidationIssue> issues) {
    super(message);
    this.issues = issues == null ? List.of() : List.copyOf(issues);
  }

  public List<TaskValidationIssue> issues() {
    return issues;
  }
}
