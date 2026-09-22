package io.yak.ops.business.sync.offline.schedule;

import io.yak.ops.common.enums.PublishState;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Keeps Yak Schedule aligned with offline Task/Schedule truth. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineScheduleLifecycle {

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineScheduleEngineBridge engine;

  public OfflineScheduleLifecycle(
      OfflineJobDefinitionRepository definitionRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineScheduleEngineBridge engine) {
    this.definitionRepository = definitionRepository;
    this.scheduleRepository = scheduleRepository;
    this.engine = engine;
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public void sync(long definitionId) {
    OfflineJobDefinition definition = definitionRepository.findById(definitionId).orElse(null);
    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    if (definition == null) {
      engine.deleteIfPresent(definitionId);
      return;
    }

    if (!schedulable(definition, schedule)) {
      engine.pauseIfPresent(definitionId);
      if (schedule != null) {
        scheduleRepository.updateRuntimeState(definitionId, schedule.lastFireTime(), null);
      }
      return;
    }

    ScheduleSnapshot snapshot = engine.save(definition, schedule);
    scheduleRepository.updateRuntimeState(
        definitionId, schedule.lastFireTime(), local(snapshot.nextFireTime()));
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public void refreshRuntimeState(long definitionId, Instant actualFireTime) {
    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    if (schedule == null) {
      return;
    }

    LocalDateTime last =
        actualFireTime == null ? schedule.lastFireTime() : local(actualFireTime);
    LocalDateTime next =
        engine
            .snapshot(definitionId)
            .filter(snapshot -> snapshot.status() == ScheduleStatus.ENABLED)
            .map(ScheduleSnapshot::nextFireTime)
            .map(this::local)
            .orElse(null);
    scheduleRepository.updateRuntimeState(definitionId, last, next);
  }

  @Transactional(transactionManager = "offlineSyncTransactionManager", rollbackFor = Exception.class)
  public void remove(long definitionId) {
    engine.deleteIfPresent(definitionId);
    OfflineSchedule schedule = scheduleRepository.findSchedule(definitionId);
    if (schedule != null) {
      scheduleRepository.updateRuntimeState(definitionId, schedule.lastFireTime(), null);
    }
  }

  private boolean schedulable(OfflineJobDefinition definition, OfflineSchedule schedule) {
    return PublishState.PUBLISHED.matches(definition.getReleaseState())
        && schedule != null
        && schedule.enabled()
        && StringUtils.hasText(schedule.cronExpression());
  }

  private LocalDateTime local(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
  }
}
