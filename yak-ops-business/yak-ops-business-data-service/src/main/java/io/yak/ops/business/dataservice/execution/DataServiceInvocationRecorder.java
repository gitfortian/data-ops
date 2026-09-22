package io.yak.ops.business.dataservice.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.domain.DataServiceDefinition;
import io.yak.ops.business.dataservice.domain.InvocationRecord;
import io.yak.ops.business.dataservice.domain.access.AccessContext;
import io.yak.ops.business.dataservice.repository.DataServiceCallLogRepository;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Persists bounded, sanitized invocation audit evidence; it does not own runtime truth. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceInvocationRecorder {

  private final DataServiceCallLogRepository repository;
  private final ObjectMapper objectMapper;
  private final DataServiceAuditSanitizer sanitizer;

  public void record(
      DataServiceDefinition definition,
      Map<String, String> parameters,
      boolean success,
      long durationMs,
      int rowCount,
      String errorMessage,
      AccessContext access) {
    AccessContext caller = access == null ? AccessContext.publicAccess() : access;
    Map<String, String> safeParameters = sanitizer.sanitize(parameters);
    repository.save(new InvocationRecord(
        null,
        definition.projectId(),
        definition.id(),
        definition.settings().name(),
        definition.settings().path(),
        caller.callerType(),
        caller.apiKeyId(),
        caller.apiKeyName(),
        caller.apiKeyPrefix(),
        limit(json(safeParameters), 4_000),
        success,
        durationMs,
        rowCount,
        limit(errorMessage, 1_000),
        LocalDateTime.now()));
  }

  private String json(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (Exception ignored) { return "{}"; }
  }

  private String limit(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
  }
}
