package io.yak.ops.business.sync.offline.schedule;

import io.yak.ops.common.enums.PublishState;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository.ProjectDefinitionRef;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 应用启动后以离线同步业务表为事实来源恢复 Yak Schedule 计划。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineScheduleReconciler {
  private static final Logger LOGGER = LoggerFactory.getLogger(OfflineScheduleReconciler.class);

  private final OfflineJobDefinitionRepository definitionRepository;
  private final OfflineScheduleRepository scheduleRepository;
  private final OfflineScheduleEngineBridge engine;
  private final OfflineScheduleLifecycle lifecycle;
  private final ProjectContextScope projectScope;

  public OfflineScheduleReconciler(
      OfflineJobDefinitionRepository definitionRepository,
      OfflineScheduleRepository scheduleRepository,
      OfflineScheduleEngineBridge engine,
      OfflineScheduleLifecycle lifecycle,
      ProjectContextScope projectScope) {
    this.definitionRepository = definitionRepository;
    this.scheduleRepository = scheduleRepository;
    this.engine = engine;
    this.lifecycle = lifecycle;
    this.projectScope = projectScope;
  }

  @Order(40)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    if (!engine.available()) {
      LOGGER.warn("[offline-schedule] Yak ScheduleManager unavailable, skip startup reconcile");
      return;
    }

    Map<Long, ScheduledDefinition> active = activeSchedules();
    engine.list().forEach(snapshot -> {
      long definitionId;
      try {
        definitionId = Long.parseLong(snapshot.definition().key().name());
      } catch (NumberFormatException exception) {
        return;
      }
      if (!active.containsKey(definitionId)) {
        try {
          engine.deleteIfPresent(definitionId);
        } catch (RuntimeException exception) {
          LOGGER.warn(
              "[offline-schedule] stale schedule cleanup failed definition={}, message={}",
              definitionId,
              exception.getMessage());
        }
      }
    });

    LocalDateTime now = LocalDateTime.now();
    active.forEach(
        (definitionId, scheduled) ->
            projectScope.run(
                new ProjectContext(scheduled.projectId(), null),
                () -> reconcileInProject(definitionId, scheduled.schedule(), now)));
  }

  private void reconcileInProject(
      long definitionId, OfflineSchedule schedule, LocalDateTime now) {
    LocalDateTime persistedNextFireTime = schedule.nextFireTime();
    try {
      lifecycle.sync(definitionId);
      LOGGER.info("[offline-schedule] reconciled definition={}", definitionId);
    } catch (RuntimeException exception) {
      LOGGER.error(
          "[offline-schedule] reconcile failed definition={}, message={}",
          definitionId,
          exception.getMessage(),
          exception);
      return;
    }

    if (persistedNextFireTime != null && !persistedNextFireTime.isAfter(now)) {
      try {
        engine.runNowIfPresent(definitionId);
        LOGGER.info(
            "[offline-schedule] recovered missed trigger definition={}, planned={}",
            definitionId,
            persistedNextFireTime);
      } catch (RuntimeException exception) {
        LOGGER.warn(
            "[offline-schedule] missed trigger recovery skipped definition={}, planned={}, message={}",
            definitionId,
            persistedNextFireTime,
            exception.getMessage());
      }
    }
  }

  private Map<Long, ScheduledDefinition> activeSchedules() {
    Map<Long, ScheduledDefinition> result = new LinkedHashMap<>();
    for (ProjectDefinitionRef candidate : definitionRepository.findScheduledForReconciliation()) {
      ScheduledDefinition scheduled =
          projectScope.call(
              new ProjectContext(candidate.projectId(), null),
              () -> activeSchedule(candidate));
      if (scheduled != null) {
        result.put(candidate.definitionId(), scheduled);
      }
    }
    return result;
  }

  private ScheduledDefinition activeSchedule(ProjectDefinitionRef candidate) {
    OfflineJobDefinition definition =
        definitionRepository.findById(candidate.definitionId()).orElse(null);
    OfflineSchedule schedule = scheduleRepository.findSchedule(candidate.definitionId());
    if (definition == null
        || definition.requireProjectId() != candidate.projectId()
        || schedule == null
        || !schedule.enabled()
        || !StringUtils.hasText(schedule.cronExpression())
        || !PublishState.PUBLISHED.matches(definition.getReleaseState())) {
      return null;
    }
    return new ScheduledDefinition(candidate.projectId(), schedule);
  }

  private record ScheduledDefinition(long projectId, OfflineSchedule schedule) {}
}
