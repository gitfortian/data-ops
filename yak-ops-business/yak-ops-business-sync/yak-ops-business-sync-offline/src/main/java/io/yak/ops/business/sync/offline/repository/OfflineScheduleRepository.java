package io.yak.ops.business.sync.offline.repository;

import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import java.time.LocalDateTime;
import java.util.List;

/** 调度配置领域仓储；数据仍直接保存在 yak_offline_job_definition。 */
public interface OfflineScheduleRepository {
  OfflineSchedule saveSchedule(OfflineSchedule schedule);
  OfflineSchedule findSchedule(Long definitionId);
  List<OfflineSchedule> findAllSchedules();
  void updateRuntimeState(Long definitionId, LocalDateTime last, LocalDateTime next);
  void deleteSchedule(Long definitionId);
}
