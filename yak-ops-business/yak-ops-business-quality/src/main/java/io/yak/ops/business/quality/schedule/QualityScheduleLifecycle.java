package io.yak.ops.business.quality.schedule;

import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleStatus;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettingsSpec;
import io.yak.ops.business.quality.repository.QualityMonitorRepository;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 数据质量调度生命周期：业务表为事实来源，Yak Schedule 只负责时间触发。 */
@ConditionalOnQualityEnabled
@Component
public class QualityScheduleLifecycle {
  private final QualityMonitorRepository repository;
  private final QualityScheduleEngineBridge engine;

  public QualityScheduleLifecycle(
      QualityMonitorRepository repository,
      QualityScheduleEngineBridge engine) {
    this.repository = repository;
    this.engine = engine;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void sync(long monitorId) {
    Monitor monitor = repository.findMonitor(monitorId).orElse(null);
    if (monitor == null) {
      engine.deleteIfPresent(monitorId);
      return;
    }
    MonitorSettings settings = repository.findMonitorSettings(monitorId);
    if (!monitor.enabled() || settings.runMode() != RunMode.SCHEDULE) {
      engine.pauseIfPresent(monitorId);
      updateNextRunTime(monitorId, settings, null);
      return;
    }
    ScheduleSnapshot snapshot = engine.save(monitor, settings);
    updateNextRunTime(monitorId, settings, snapshot.nextFireTime());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void refreshRuntimeState(long monitorId) {
    MonitorSettings settings = repository.findMonitorSettings(monitorId);
    Instant next = engine.snapshot(monitorId)
        .filter(snapshot -> snapshot.status() == ScheduleStatus.ENABLED)
        .map(ScheduleSnapshot::nextFireTime)
        .orElse(null);
    updateNextRunTime(monitorId, settings, next);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void remove(long monitorId) {
    engine.deleteIfPresent(monitorId);
    MonitorSettings settings = repository.findMonitorSettings(monitorId);
    updateNextRunTime(monitorId, settings, null);
  }

  private void updateNextRunTime(long monitorId, MonitorSettings settings, Instant nextFireTime) {
    LocalDateTime next = nextFireTime == null
        ? null
        : LocalDateTime.ofInstant(nextFireTime, ZoneId.systemDefault());
    repository.upsertMonitorSettings(
        monitorId,
        new MonitorSettingsSpec(
            settings.runMode(), settings.scheduleFrequency(), settings.scheduleTime(),
            settings.scheduleWeekday(), settings.cronExpression(), next,
            settings.ruleFailureAction(), settings.notifyEnabled(), settings.notifyChannel(),
            settings.notifyTarget(), settings.alertLevel()));
  }
}
