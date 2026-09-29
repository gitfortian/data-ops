package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 将任务级调度投影持久化到任务定义表。 */
@ConditionalOnOfflineSyncEnabled
@Repository
@RequiredArgsConstructor
public class OfflineScheduleRepositoryAdapter implements OfflineScheduleRepository {
  private final OfflineJobDefinitionDao dao;
  private final CurrentProject currentProject;

  @Override
  public OfflineSchedule saveSchedule(OfflineSchedule schedule) {
    requireProject();
    if (schedule == null || schedule.jobDefinitionId() == null) {
      throw new IllegalArgumentException("调度配置缺少任务定义 ID");
    }
    LocalDateTime now = LocalDateTime.now();
    if (!dao.updateSchedule(
        schedule.jobDefinitionId(),
        schedule.scheduleJson(),
        schedule.enabled(),
        schedule.cronExpression(),
        Math.max(1, schedule.retryMaxAttempts()),
        Math.max(1, schedule.retryBackoffSeconds()),
        schedule.nextFireTime(),
        now)) {
      throw new IllegalArgumentException("离线同步任务不存在：" + schedule.jobDefinitionId());
    }
    return findSchedule(schedule.jobDefinitionId());
  }

  @Override
  public OfflineSchedule findSchedule(Long definitionId) {
    requireProject();
    return from(dao.selectById(definitionId));
  }

  @Override
  public List<OfflineSchedule> findAllSchedules() {
    requireProject();
    return dao.selectWithCron().stream().map(this::from).toList();
  }

  @Override
  public void updateRuntimeState(Long definitionId, LocalDateTime last, LocalDateTime next) {
    requireProject();
    dao.updateScheduleRuntime(definitionId, last, next, LocalDateTime.now());
  }

  @Override
  public void deleteSchedule(Long definitionId) {
    requireProject();
    dao.clearSchedule(definitionId, LocalDateTime.now());
  }

  private OfflineSchedule from(OfflineJobDefinitionPO po) {
    if (po == null) return null;
    return new OfflineSchedule(
        po.getId(),
        po.getCronExpression(),
        Boolean.TRUE.equals(po.getScheduleEnabled()),
        po.getRetryMaxAttempts() == null ? 1 : po.getRetryMaxAttempts(),
        po.getRetryBackoffSeconds() == null ? 60 : po.getRetryBackoffSeconds(),
        po.getScheduleNextFireTime(),
        po.getScheduleLastFireTime(),
        po.getScheduleJson());
  }

  private void requireProject() {
    currentProject.requireProjectId();
  }
}
