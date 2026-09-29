package io.yak.ops.business.quality.schedule;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.MonitorSettings;
import io.yak.ops.common.enums.quality.QualityEnums.RunMode;
import io.yak.ops.common.schedule.YakScheduleGateway;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import io.yak.ops.core.project.CurrentProject;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Yak Ops 数据质量调度到 Yak Framework ScheduleManager 的适配层。 */
@Component
public class QualityScheduleEngineBridge {
  static final String NAMESPACE = YakScheduleNamespaces.DATA_QUALITY;
  static final String HANDLER = "qualityScheduleHandler";

  private final YakScheduleGateway gateway;
  private final QualityScheduleCalculator calculator;
  private final CurrentProject currentProject;

  public QualityScheduleEngineBridge(
      ObjectProvider<ScheduleManager> scheduleManagers,
      QualityScheduleCalculator calculator,
      CurrentProject currentProject) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
    this.calculator = calculator;
    this.currentProject = currentProject;
  }

  public boolean available() {
    return gateway.available();
  }

  public ScheduleSnapshot save(Monitor monitor, MonitorSettings settings) {
    return gateway.save(toDefinition(monitor, settings));
  }

  public Optional<ScheduleSnapshot> snapshot(long monitorId) {
    return gateway.snapshot(name(monitorId));
  }

  public List<ScheduleSnapshot> list() {
    return gateway.list();
  }

  public void pauseIfPresent(long monitorId) {
    gateway.pauseIfPresent(name(monitorId));
  }

  public void deleteIfPresent(long monitorId) {
    gateway.deleteIfPresent(name(monitorId));
  }

  public void runNowIfPresent(long monitorId) {
    gateway.runNowIfPresent(name(monitorId));
  }

  ScheduleDefinition toDefinition(Monitor monitor, MonitorSettings settings) {
    if (monitor == null || monitor.id() == null) {
      throw new IllegalArgumentException("质量监控不能为空");
    }
    if (settings == null || settings.runMode() != RunMode.SCHEDULE) {
      throw new IllegalArgumentException("质量监控未配置调度触发");
    }

    long projectId = currentProject.requireProjectId();
    String cron =
        calculator.cronExpression(
            settings.scheduleFrequency(),
            settings.scheduleTime(),
            settings.scheduleWeekday(),
            settings.cronExpression());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("projectId", projectId);
    payload.put("monitorId", monitor.id());

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("projectId", String.valueOf(projectId));
    metadata.put("monitorId", String.valueOf(monitor.id()));
    metadata.put("dataSourceId", String.valueOf(monitor.dataSourceId()));
    metadata.put("tableName", monitor.tableName());

    return new ScheduleDefinition(
        key(monitor.id()),
        monitor.name(),
        ScheduleTrigger.cron(cron, ZoneId.systemDefault()),
        new ScheduleTarget(HANDLER, payload),
        SchedulePolicy.defaults(),
        monitor.enabled(),
        metadata);
  }

  private ScheduleKey key(long monitorId) {
    return gateway.key(name(monitorId));
  }

  private String name(long monitorId) {
    if (monitorId <= 0L) throw new IllegalArgumentException("质量监控 ID 不合法");
    return String.valueOf(monitorId);
  }
}
