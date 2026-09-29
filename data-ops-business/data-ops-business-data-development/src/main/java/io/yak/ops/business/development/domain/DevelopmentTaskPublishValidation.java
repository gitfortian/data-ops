package io.yak.ops.business.development.domain;

import io.yak.ops.plugin.task.api.TaskValidationIssue;
import java.util.List;

/** Revision-bound preflight result shown before an explicit task Publish action. */
public record DevelopmentTaskPublishValidation(
    Long nodeId,
    long draftRevision,
    boolean valid,
    String message,
    List<TaskValidationIssue> issues) {

  public DevelopmentTaskPublishValidation {
    issues = issues == null ? List.of() : List.copyOf(issues);
  }
}
