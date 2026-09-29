package io.yak.ops.business.workflow.schedule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class WorkflowDefinitionScheduleGuardTest {

  @Mock private ObjectProvider<WorkflowScheduleDao> scheduleDaoProvider;
  @Mock private ObjectProvider<WorkflowScheduleAuditCoordinator> scheduleAuditProvider;
  @Mock private WorkflowScheduleDao scheduleDao;
  @Mock private WorkflowScheduleAuditCoordinator scheduleAudit;

  private WorkflowDefinitionScheduleGuard guard;

  @BeforeEach
  void setUp() {
    guard = new WorkflowDefinitionScheduleGuard(scheduleDaoProvider, scheduleAuditProvider);
  }

  @Test
  void shouldKeepLightweightBehaviorWhenScheduleComponentsAreUnavailable() {
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(null);

    assertThatCode(() -> guard.activateConfiguredSchedules("workflow-1"))
        .doesNotThrowAnyException();
    assertThatCode(() -> guard.deactivateConfiguredSchedules("workflow-1"))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldActivateSavedSchedulesWhenWorkflowGoesOnline() {
    WorkflowSchedulePO schedule = schedule("schedule-1", "OFFLINE");
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleAuditProvider.getIfAvailable()).thenReturn(scheduleAudit);
    when(scheduleDao.selectSchedules("workflow-1", "OFFLINE")).thenReturn(List.of(schedule));

    guard.activateConfiguredSchedules("workflow-1");

    verify(scheduleAudit).onlineFromWorkflow("schedule-1", "workflow-1");
  }

  @Test
  void shouldSkipExpiredScheduleWhenWorkflowGoesOnline() {
    WorkflowSchedulePO schedule = schedule("schedule-expired", "OFFLINE");
    schedule.setEndTime(Instant.now().minus(1, ChronoUnit.DAYS));
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleAuditProvider.getIfAvailable()).thenReturn(scheduleAudit);
    when(scheduleDao.selectSchedules("workflow-1", "OFFLINE")).thenReturn(List.of(schedule));

    guard.activateConfiguredSchedules("workflow-1");

    verifyNoInteractions(scheduleAudit);
  }

  @Test
  void shouldDeactivateOnlineSchedulesBeforeWorkflowGoesOffline() {
    WorkflowSchedulePO schedule = schedule("schedule-1", "ONLINE");
    when(scheduleDaoProvider.getIfAvailable()).thenReturn(scheduleDao);
    when(scheduleAuditProvider.getIfAvailable()).thenReturn(scheduleAudit);
    when(scheduleDao.selectSchedules("workflow-1", "ONLINE")).thenReturn(List.of(schedule));

    guard.deactivateConfiguredSchedules("workflow-1");

    verify(scheduleAudit).offlineFromWorkflow("schedule-1", "workflow-1");
  }

  private WorkflowSchedulePO schedule(String id, String status) {
    WorkflowSchedulePO schedule = new WorkflowSchedulePO();
    schedule.setId(id);
    schedule.setWorkflowId("workflow-1");
    schedule.setStatus(status);
    return schedule;
  }
}
