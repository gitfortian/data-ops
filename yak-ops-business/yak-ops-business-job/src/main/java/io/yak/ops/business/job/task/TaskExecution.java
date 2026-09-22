package io.yak.ops.business.job.task;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Workflow and manual runs observe every task type through this stable execution view. */
public record TaskExecution(
    String executionId,
    String status,
    String errorMessage,
    Map<String, Object> output) {

  public TaskExecution {
    if (executionId == null || executionId.isBlank()) {
      throw new IllegalArgumentException("executionId 不能为空");
    }
    output = output == null
        ? Map.of()
        : Collections.unmodifiableMap(new LinkedHashMap<>(output));
  }

  public boolean terminal() {
    String normalized = normalizedStatus();
    return "SUCCEEDED".equals(normalized)
        || "FAILED".equals(normalized)
        || "CANCELED".equals(normalized)
        || "TIMED_OUT".equals(normalized)
        || "LOST".equals(normalized);
  }

  public boolean successful() {
    return "SUCCEEDED".equals(normalizedStatus());
  }

  private String normalizedStatus() {
    return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
  }
}
