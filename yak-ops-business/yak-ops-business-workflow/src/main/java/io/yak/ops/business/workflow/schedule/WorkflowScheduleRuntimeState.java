package io.yak.ops.business.workflow.schedule;

import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.ops.business.workflow.dao.WorkflowScheduleDao;
import io.yak.ops.common.bean.po.workflow.WorkflowSchedulePO;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** 仅维护调度运行态时间，不改写调度配置更新时间。 */
@Component
public class WorkflowScheduleRuntimeState {
  private final WorkflowScheduleDao dao;

  public WorkflowScheduleRuntimeState(WorkflowScheduleDao dao) {
    this.dao = dao;
  }

  public void applySnapshot(WorkflowSchedulePO schedule, ScheduleSnapshot snapshot) {
    if (snapshot == null) return;
    if (snapshot.lastFireTime() != null) schedule.setLastFireTime(snapshot.lastFireTime());
    schedule.setNextFireTime(normalizeNext(schedule, snapshot.nextFireTime()));
  }

  public void syncSnapshot(WorkflowSchedulePO schedule, ScheduleSnapshot snapshot) {
    if (schedule == null || snapshot == null) return;
    Instant last = snapshot.lastFireTime() == null
        ? schedule.getLastFireTime()
        : snapshot.lastFireTime();
    update(schedule.getId(), last, normalizeNext(schedule, snapshot.nextFireTime()));
  }

  public void recordFire(String scheduleId, Instant fireTime, Instant nextFireTime) {
    WorkflowSchedulePO schedule = dao.selectSchedule(scheduleId);
    if (schedule == null) return;
    update(scheduleId, fireTime, normalizeNext(schedule, nextFireTime));
  }

  public void clearNext(String scheduleId) {
    WorkflowSchedulePO schedule = dao.selectSchedule(scheduleId);
    if (schedule == null) return;
    update(scheduleId, schedule.getLastFireTime(), null);
  }

  private Instant normalizeNext(WorkflowSchedulePO schedule, Instant next) {
    if (next == null) return null;
    Instant end = schedule.getEndTime();
    return end != null && next.isAfter(end) ? null : next;
  }

  private void update(String scheduleId, Instant lastFireTime, Instant nextFireTime) {
    if (dao.updateRuntimeState(scheduleId, lastFireTime, nextFireTime) != 1) {
      throw new IllegalStateException("更新工作流调度运行态失败：" + scheduleId);
    }
  }
}
