package io.yak.ops.business.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Full Audit Center drawer model: immutable operation snapshot plus ordered Timeline. */
public record AuditOperationDetail(
    AuditOperationSummary operation,
    Map<String, Object> metadata,
    List<AuditTimelineEvent> events) {

  public AuditOperationDetail {
    metadata =
        metadata == null || metadata.isEmpty()
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    events = events == null ? List.of() : List.copyOf(events);
  }
}
