package io.yak.ops.business.workflow.schedule;

import io.yak.ops.business.workflow.schedule.trigger.WorkflowScheduleTriggerCoordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowScheduleTriggerDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import io.yak.ops.common.bean.po.workflow.WorkflowScheduleTriggerPO;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowScheduleMisfireRecoveryTest {
  @Mock private WorkflowScheduleTriggerCoordinator coordinator;
  @Mock private WorkflowScheduleTriggerDao ledger;

  private WorkflowScheduleMisfireRecovery recovery;

  @BeforeEach
  void setUp() {
    recovery = new WorkflowScheduleMisfireRecovery(coordinator, ledger);
  }

  @Test
  void shouldFireOnceThroughCoordinator() {
    WorkflowSchedulePO schedule = schedule("FIRE_ONCE");
    Instant missed = Instant.parse("2026-08-14T01:00:00Z");
    Instant recovered = Instant.parse("2026-08-14T03:00:00Z");

    recovery.recover(schedule, missed, recovered);

    verify(coordinator).recoverMisfire(schedule, missed, recovered);
  }

  @Test
  void shouldPersistSkippedMisfireInLedger() {
    WorkflowSchedulePO schedule = schedule("SKIP");
    Instant missed = Instant.parse("2026-08-14T01:00:00Z");
    Instant recovered = Instant.parse("2026-08-14T03:00:00Z");
    when(ledger.claim(org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    recovery.recover(schedule, missed, recovered);

    ArgumentCaptor<WorkflowScheduleTriggerPO> captor =
        ArgumentCaptor.forClass(WorkflowScheduleTriggerPO.class);
    verify(ledger).claim(captor.capture());
    WorkflowScheduleTriggerPO trigger = captor.getValue();
    assertThat(trigger.getStatus()).isEqualTo("SKIPPED");
    assertThat(trigger.getTriggerSource()).isEqualTo("MISFIRE_RECOVERY");
    assertThat(trigger.getPlannedFireTime()).isEqualTo(missed);
    assertThat(trigger.getDedupeKey()).isEqualTo("schedule-1|SCHEDULE|1786669200000");
    assertThat(trigger.getBusinessDate()).isEqualTo(LocalDate.of(2026, 8, 14));
    assertThat(trigger.getCompletedAt()).isEqualTo(recovered);
  }

  private WorkflowSchedulePO schedule(String misfire) {
    WorkflowSchedulePO value = new WorkflowSchedulePO();
    value.setId("schedule-1");
    value.setWorkflowId("workflow-1");
    value.setTimezone("Asia/Shanghai");
    value.setExecutionStrategy("SERIAL_WAIT");
    value.setMisfireStrategy(misfire);
    return value;
  }
}
