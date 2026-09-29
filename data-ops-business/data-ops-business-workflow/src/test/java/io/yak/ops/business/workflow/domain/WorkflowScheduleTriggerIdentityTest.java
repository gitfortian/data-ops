package io.yak.ops.business.workflow.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WorkflowScheduleTriggerIdentityTest {

  @Test
  void shouldKeepNormalScheduleIdempotentAndIsolateBackfillBatches() {
    Instant planned = Instant.parse("2026-08-14T02:00:00Z");

    String scheduled = WorkflowScheduleTriggerIdentity.scheduled("schedule-1", planned);
    String scheduledAgain = WorkflowScheduleTriggerIdentity.scheduled("schedule-1", planned);
    String backfillOne = WorkflowScheduleTriggerIdentity.backfill("schedule-1", "backfill-1", planned);
    String backfillOneAgain = WorkflowScheduleTriggerIdentity.backfill("schedule-1", "backfill-1", planned);
    String backfillTwo = WorkflowScheduleTriggerIdentity.backfill("schedule-1", "backfill-2", planned);

    assertThat(scheduledAgain).isEqualTo(scheduled);
    assertThat(backfillOneAgain).isEqualTo(backfillOne);
    assertThat(backfillOne).isNotEqualTo(scheduled);
    assertThat(backfillTwo).isNotEqualTo(backfillOne);
  }
}
