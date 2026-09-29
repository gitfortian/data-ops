package io.yak.ops.business.quality.schedule;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.business.quality.repository.QualityScheduleRecoveryRepository;
import io.yak.ops.business.quality.repository.QualityScheduleRecoveryRepository.ProjectMonitorRef;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.core.project.ProjectContext;
import io.yak.ops.core.project.ProjectContextScope;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 应用启动后按持久化 Project 恢复数据质量调度计划。 */
@ConditionalOnQualityEnabled
@Component
public class QualityScheduleReconciler {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(QualityScheduleReconciler.class);

  private final QualityMonitorRepository repository;
  private final QualityScheduleRecoveryRepository recoveryRepository;
  private final QualityScheduleEngineBridge engine;
  private final QualityScheduleLifecycle lifecycle;
  private final ProjectContextScope projectScope;

  public QualityScheduleReconciler(
      QualityMonitorRepository repository,
      QualityScheduleRecoveryRepository recoveryRepository,
      QualityScheduleEngineBridge engine,
      QualityScheduleLifecycle lifecycle,
      ProjectContextScope projectScope) {
    this.repository = repository;
    this.recoveryRepository = recoveryRepository;
    this.engine = engine;
    this.lifecycle = lifecycle;
    this.projectScope = projectScope;
  }

  @Order(30)
  @EventListener(ApplicationReadyEvent.class)
  public void reconcile() {
    if (!engine.available()) {
      LOGGER.warn(
          "[quality-schedule] Yak ScheduleManager unavailable, skip startup reconcile");
      return;
    }

    List<ProjectMonitorRef> candidates = recoveryRepository.listScheduledMonitors();
    cleanupStaleSchedules(candidates);

    LocalDateTime now = LocalDateTime.now();
    for (ProjectMonitorRef candidate : candidates) {
      try {
        projectScope.run(
            new ProjectContext(candidate.projectId(), null),
            () -> reconcileInProject(candidate, now));
      } catch (RuntimeException exception) {
        LOGGER.error(
            "[quality-schedule] reconcile failed projectId={}, monitor={}, message={}",
            candidate.projectId(),
            candidate.monitorId(),
            exception.getMessage(),
            exception);
      }
    }
  }

  private void cleanupStaleSchedules(List<ProjectMonitorRef> candidates) {
    Set<Long> expectedMonitorIds = new HashSet<>();
    candidates.forEach(candidate -> expectedMonitorIds.add(candidate.monitorId()));
    engine.list().forEach(
        snapshot -> {
          long monitorId;
          try {
            monitorId = Long.parseLong(snapshot.definition().key().name());
          } catch (NumberFormatException exception) {
            return;
          }
          if (expectedMonitorIds.contains(monitorId)) return;
          try {
            engine.deleteIfPresent(monitorId);
          } catch (RuntimeException exception) {
            LOGGER.warn(
                "[quality-schedule] stale schedule cleanup failed monitor={}, message={}",
                monitorId,
                exception.getMessage());
          }
        });
  }

  private void reconcileInProject(
      ProjectMonitorRef candidate,
      LocalDateTime now) {
    Monitor monitor = repository.findMonitor(candidate.monitorId()).orElse(null);
    if (monitor == null) {
      engine.deleteIfPresent(candidate.monitorId());
      return;
    }

    MonitorSettings settings =
        repository.findMonitorSettings(candidate.monitorId());
    LocalDateTime persistedNextRunTime = settings.nextRunTime();
    lifecycle.sync(candidate.monitorId());
    LOGGER.info(
        "[quality-schedule] reconciled projectId={}, monitor={}",
        candidate.projectId(),
        candidate.monitorId());

    if (monitor.enabled()
        && settings.runMode() == RunMode.SCHEDULE
        && persistedNextRunTime != null
        && !persistedNextRunTime.isAfter(now)) {
      try {
        engine.runNowIfPresent(candidate.monitorId());
        LOGGER.info(
            "[quality-schedule] recovered missed trigger projectId={}, monitor={}, planned={}",
            candidate.projectId(),
            candidate.monitorId(),
            persistedNextRunTime);
      } catch (RuntimeException exception) {
        LOGGER.warn(
            "[quality-schedule] missed trigger recovery skipped projectId={}, monitor={}, planned={}, message={}",
            candidate.projectId(),
            candidate.monitorId(),
            persistedNextRunTime,
            exception.getMessage());
      }
    }
  }
}
