package io.yak.ops.business.dataservice.domain;

import java.time.LocalDateTime;

/** Immutable business projection of one invocation audit event. */
public record InvocationRecord(
    Long id,
    Long projectId,
    Long apiId,
    String serviceName,
    String servicePath,
    String callerType,
    Long apiKeyId,
    String apiKeyName,
    String apiKeyPrefix,
    String paramsJson,
    boolean success,
    long durationMs,
    int rowCount,
    String errorMessage,
    LocalDateTime createTime) {

  /** @deprecated New audit evidence must carry the owning Project Space. */
  @Deprecated(forRemoval = false)
  public InvocationRecord(
      Long id,
      Long apiId,
      String serviceName,
      String servicePath,
      String callerType,
      Long apiKeyId,
      String apiKeyName,
      String apiKeyPrefix,
      String paramsJson,
      boolean success,
      long durationMs,
      int rowCount,
      String errorMessage,
      LocalDateTime createTime) {
    this(
        id,
        null,
        apiId,
        serviceName,
        servicePath,
        callerType,
        apiKeyId,
        apiKeyName,
        apiKeyPrefix,
        paramsJson,
        success,
        durationMs,
        rowCount,
        errorMessage,
        createTime);
  }
}
