package io.yak.ops.business.metadata.autoingest;

import io.yak.ops.business.metadata.dao.mapper.AutoIngestEventMapper;

import io.yak.ops.business.asset.api.MetadataAutoIngestApi;
import io.yak.ops.business.metadata.config.ConditionalOnMetadataPersistence;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * S2 durable positive-evidence worker. Independent DB statements implement:
 * committed claim -> Asset-owned transactional upsert -> durable acknowledgment.
 * If ack is lost after Asset commit, a lease retry repeats an idempotent upsert.
 * This worker NEVER invokes full-provider reconcile or GONE detection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnMetadataPersistence
@ConditionalOnProperty(prefix = "yak.metadata.auto-ingest", name = "enabled",
    havingValue = "true", matchIfMissing = false)
public class AutoIngestDeliveryWorker {
  static final int BATCH_SIZE = 32;
  static final int MAX_ATTEMPTS = 8;
  private final AutoIngestEventMapper events;
  private final MetadataAutoIngestApi asset;
  private final ProjectContextScope scope;

  @Scheduled(fixedDelayString = "${yak.metadata.auto-ingest.poll-ms:15000}")
  public void runOnce() {
    LocalDateTime now = LocalDateTime.now();
    events.releaseExpired(now);
    List<AutoIngestEvent> ready = events.ready(now, BATCH_SIZE);
    for (AutoIngestEvent event : ready) {
      deliver(event);
    }
  }

  void deliver(AutoIngestEvent event) {
    if (event == null || event.getProjectId() == null
        || event.getProjectId() <= 0 || event.getEntityId() == null
        || event.getEntityId() <= 0 || event.getRunId() == null) {
      // Refuse any malformed evidence. The original record remains inspectable.
      log.error("Reject invalid automatic asset ingest event {}", event == null ? null : event.getId());
      return;
    }
    String token = UUID.randomUUID().toString();
    LocalDateTime claimedAt = LocalDateTime.now();
    if (events.claim(event.getId(), token, claimedAt.plusMinutes(5), claimedAt) != 1) {
      return; // another worker owns it
    }
    int attempts = (event.getAttemptCount() == null ? 0 : event.getAttemptCount()) + 1;
    try {
      AtomicReference<MetadataAutoIngestApi.ScopedIngestReceipt> receipt = new AtomicReference<>();
      scope.run(new ProjectContext(event.getProjectId(), null), () ->
          receipt.set(asset.ingestCommittedMetadataTable(
              event.getProjectId(), event.getEntityId(), "metadata-auto-ingest")));
      if (receipt.get() == null
          || !event.getProjectId().equals(receipt.get().projectId())
          || !event.getEntityId().equals(receipt.get().metadataEntityId())) {
        throw new IllegalStateException("Asset Owner 回执不匹配，不能确认自动纳管成功");
      }
      if (events.succeed(event.getId(), token, LocalDateTime.now()) != 1) {
        log.warn("Auto-ingest ACK lease changed; idempotent replay required event={}", event.getId());
      }
    } catch (RuntimeException failure) {
      long delaySeconds = Math.min(3600L, 15L << Math.min(attempts, 7));
      LocalDateTime at = LocalDateTime.now();
      String reason = failure.getMessage() == null
          ? failure.getClass().getSimpleName() : failure.getMessage();
      String boundedReason = reason.substring(0, Math.min(1024, reason.length()));
      String nextStatus = attempts >= MAX_ATTEMPTS ? "DEAD" : "RETRY";
      events.fail(event.getId(), token, nextStatus,
          at.plusSeconds(delaySeconds), boundedReason, at);
      log.warn("Auto-ingest {} event={} project={} run={} entity={} attempts={}: {}",
          nextStatus, event.getId(), event.getProjectId(), event.getRunId(),
          event.getEntityId(), attempts, boundedReason);
    }
  }
}
