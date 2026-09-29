package io.yak.ops.business.development.task;

import io.yak.ops.plugin.task.api.TaskValidationIssue;
import java.util.List;

/** Task-owned validation failure; extends the legacy compatibility type for existing callers. */
public class DevelopmentTaskValidationException
    extends io.yak.ops.business.development.service.DevelopmentTaskValidationException {

  public DevelopmentTaskValidationException(String message, List<TaskValidationIssue> issues) {
    super(message, issues);
  }
}
