package io.yak.ops.business.metadata.autoingest;

import java.time.LocalDateTime;
import lombok.Data;

/** Durable committed-table delivery state, not a second Metadata entity or Asset ledger. */
@Data
public class AutoIngestEvent {
  private Long id;
  private Long projectId;
  private Long runId;
  private Long entityId;
  private String status;
  private Integer attemptCount;
  private LocalDateTime nextAttemptAt;
  private LocalDateTime leaseUntil;
  private String claimToken;
  private String errorMessage;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
