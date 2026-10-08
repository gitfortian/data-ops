package io.yak.ops.business.dataservice.observability;

import io.yak.ops.business.dataservice.domain.InvocationRecord;
import java.time.LocalDateTime;

/**
 * Read-only execution evidence for the exact persisted invocation. SQL BIGINT
 * IDs must cross JSON as decimal strings rather than IEEE-754 numbers.
 * Missing/foreign Project or API records intentionally share NOT_FOUND.
 */
public record InvocationEvidenceView(State state, Evidence record) {
  public enum State { FOUND, NOT_FOUND }

  public static InvocationEvidenceView notFound() {
    return new InvocationEvidenceView(State.NOT_FOUND, null);
  }

  public static InvocationEvidenceView found(InvocationRecord source) {
    return new InvocationEvidenceView(
        State.FOUND,
        new Evidence(
            decimal(source.id()), decimal(source.apiId()),
            source.serviceName(), source.servicePath(), source.callerType(),
            decimal(source.apiKeyId()), decimal(source.consumerId()),
            source.apiKeyName(), source.apiKeyPrefix(),
            decimal(source.sourceRevisionId()), source.sourceRevisionNo(),
            source.paramsJson(), source.success(), source.durationMs(),
            source.rowCount(), source.errorMessage(), source.createTime()));
  }

  private static String decimal(Long value) {
    return value == null ? null : value.toString();
  }

  public record Evidence(
      String id,
      String apiId,
      String serviceName,
      String servicePath,
      String callerType,
      String apiKeyId,
      String consumerId,
      String apiKeyName,
      String apiKeyPrefix,
      String sourceRevisionId,
      Integer sourceRevisionNo,
      String paramsJson,
      boolean success,
      long durationMs,
      int rowCount,
      String errorMessage,
      LocalDateTime createTime) {}
}
