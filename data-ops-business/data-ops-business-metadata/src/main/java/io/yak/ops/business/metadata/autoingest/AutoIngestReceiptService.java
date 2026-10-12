package io.yak.ops.business.metadata.autoingest;

import io.yak.ops.business.metadata.dao.mapper.AutoIngestEventMapper;
import io.yak.ops.business.metadata.dao.model.AutoIngestEvent;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Metadata-owned public application read/repair boundary for durable delivery.
 * Controllers must not query the Metadata DAO or expose claim tokens.
 */
@Service
@RequiredArgsConstructor
public class AutoIngestReceiptService {
  private final AutoIngestEventMapper events;

  public Receipt latest(Long projectId, Long entityId) {
    AutoIngestEvent event = entityId == null || entityId <= 0
        ? null : events.latest(projectId, entityId);
    return event == null
        ? new Receipt(null, null, entityId, "NEVER", 0, null, null)
        : new Receipt(event.getId(), event.getRunId(), event.getEntityId(),
            event.getStatus(), event.getAttemptCount(),
            event.getErrorMessage(), event.getUpdatedAt());
  }

  public boolean retryDead(Long projectId, Long eventId) {
    return eventId != null && eventId > 0
        && events.retryDead(projectId, eventId, LocalDateTime.now()) == 1;
  }

  /** Durable attempt and status; current Asset ledger truth remains in Asset Owner. */
  public record Receipt(Long eventId, Long runId, Long entityId, String status,
                        Integer attemptCount, String errorMessage, LocalDateTime updatedAt) {}
}
