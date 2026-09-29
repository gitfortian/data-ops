package io.yak.ops.plugin.task.api;

/** One structured task-definition validation issue. */
public record TaskValidationIssue(
    String code,
    String field,
    String message) {

  public TaskValidationIssue {
    if (code == null || code.trim().isEmpty()) {
      throw new IllegalArgumentException("code must not be blank");
    }
    code = code.trim();
    field = field == null || field.trim().isEmpty() ? null : field.trim();
    if (message == null || message.trim().isEmpty()) {
      throw new IllegalArgumentException("message must not be blank");
    }
    message = message.trim();
  }
}
