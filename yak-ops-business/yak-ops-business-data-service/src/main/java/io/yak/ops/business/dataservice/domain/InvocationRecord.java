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
    Long consumerId,
    String apiKeyName,
    String apiKeyPrefix,
    Long sourceRevisionId,
    Integer sourceRevisionNo,
    String paramsJson,
    boolean success,
    long durationMs,
    int rowCount,
    String errorMessage,
    LocalDateTime createTime) {

  /** Compatibility constructor for audit rows created before consumer/revision evidence was captured. */
  public InvocationRecord(
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
    this(
        id, projectId, apiId, serviceName, servicePath, callerType, apiKeyId, null,
        apiKeyName, apiKeyPrefix, null, null, paramsJson, success, durationMs, rowCount,
        errorMessage, createTime);
  }

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
        id, null, apiId, serviceName, servicePath, callerType, apiKeyId, null,
        apiKeyName, apiKeyPrefix, null, null, paramsJson, success, durationMs, rowCount,
        errorMessage, createTime);
  }
}
