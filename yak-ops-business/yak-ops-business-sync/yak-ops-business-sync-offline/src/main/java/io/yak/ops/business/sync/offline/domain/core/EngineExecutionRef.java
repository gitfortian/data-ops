package io.yak.ops.business.sync.offline.domain.core;

/** Generic evidence that links an Attempt to an external engine execution. */
public record EngineExecutionRef(String jobId, String workerInstanceId) {

  public EngineExecutionRef {
    jobId = trim(jobId);
    workerInstanceId = trim(workerInstanceId);
    if (jobId == null && workerInstanceId == null) {
      throw new IllegalArgumentException("EngineExecutionRef 至少需要一个外部标识");
    }
  }

  private static String trim(String value) {
    return value == null || value.trim().isEmpty() ? null : value.trim();
  }
}
