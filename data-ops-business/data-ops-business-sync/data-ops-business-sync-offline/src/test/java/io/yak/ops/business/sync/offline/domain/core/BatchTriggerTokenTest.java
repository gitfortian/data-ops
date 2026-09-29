package io.yak.ops.business.sync.offline.domain.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BatchTriggerTokenTest {

  @Test
  void scheduleTokenCarriesStableScheduleIdentity() {
    Instant plannedFireTime = Instant.parse("2026-08-23T03:15:00Z");

    String token = BatchTriggerToken.schedule("offline-sync:42", plannedFireTime);
    BatchTriggerToken.Parsed parsed = BatchTriggerToken.parse(token);

    assertEquals("SCHEDULE", parsed.attemptTriggerType());
    assertEquals(BatchTrigger.SCHEDULE, parsed.batchTrigger());
    assertEquals(BatchKey.schedule("offline-sync:42", plannedFireTime), parsed.batchKey());
  }

  @Test
  void retryRemainsAnExistingBatchAttemptTrigger() {
    BatchTriggerToken.Parsed parsed = BatchTriggerToken.parse("RETRY");

    assertEquals("RETRY", parsed.attemptTriggerType());
    assertEquals(null, parsed.batchTrigger());
    assertEquals(null, parsed.batchKey());
  }
}
