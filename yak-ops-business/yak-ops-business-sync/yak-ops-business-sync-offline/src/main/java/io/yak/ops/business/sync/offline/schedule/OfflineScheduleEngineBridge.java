package io.yak.ops.business.sync.offline.schedule;

import io.yak.ops.common.enums.PublishState;
import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import io.yak.ops.business.sync.offline.domain.OfflineJobDefinition;
import io.yak.ops.business.sync.offline.domain.OfflineSchedule;
import io.yak.ops.common.schedule.YakScheduleGateway;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 离线同步业务调度到 Yak Framework ScheduleManager 的适配层。 */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineScheduleEngineBridge {
  static final String NAMESPACE = YakScheduleNamespaces.OFFLINE_SYNC;
  static final String HANDLER = "offlineSyncScheduleHandler";

  private final YakScheduleGateway gateway;

  public OfflineScheduleEngineBridge(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
  }

  public boolean available() {
    return gateway.available();
  }

  public ScheduleSnapshot save(OfflineJobDefinition definition, OfflineSchedule schedule) {
    return gateway.save(toDefinition(definition, schedule));
  }

  public Optional<ScheduleSnapshot> snapshot(long definitionId) {
    return gateway.snapshot(name(definitionId));
  }

  public List<ScheduleSnapshot> list() {
    return gateway.list();
  }

  public void pauseIfPresent(long definitionId) {
    gateway.pauseIfPresent(name(definitionId));
  }

  public void deleteIfPresent(long definitionId) {
    gateway.deleteIfPresent(name(definitionId));
  }

  public void runNowIfPresent(long definitionId) {
    gateway.runNowIfPresent(name(definitionId));
  }

  ScheduleDefinition toDefinition(OfflineJobDefinition definition, OfflineSchedule schedule) {
    if (definition == null || definition.getId() == null || definition.getId() <= 0L) {
      throw new IllegalArgumentException("离线同步任务不能为空");
    }
    if (schedule == null || !StringUtils.hasText(schedule.cronExpression())) {
      throw new IllegalArgumentException("离线同步任务未配置 Cron 调度");
    }

    long projectId = definition.requireProjectId();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("definitionId", definition.getId());
    payload.put("projectId", projectId);

    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("source", "yak-ops");
    metadata.put("definitionId", String.valueOf(definition.getId()));
    metadata.put("projectId", String.valueOf(projectId));
    put(metadata, "mode", definition.getMode());
    put(metadata, "sourceType", definition.getSourceType());
    put(metadata, "sinkType", definition.getSinkType());

    boolean enabled = PublishState.PUBLISHED.matches(definition.getReleaseState()) && schedule.enabled();
    return new ScheduleDefinition(
        key(definition.getId()),
        definition.getJobName(),
        ScheduleTrigger.cron(schedule.cronExpression(), ZoneId.systemDefault()),
        new ScheduleTarget(HANDLER, payload),
        SchedulePolicy.defaults(),
        enabled,
        metadata);
  }

  private ScheduleKey key(long definitionId) {
    return gateway.key(name(definitionId));
  }

  private String name(long definitionId) {
    if (definitionId <= 0L) throw new IllegalArgumentException("离线同步任务 ID 不合法");
    return String.valueOf(definitionId);
  }

  private void put(Map<String, String> metadata, String key, String value) {
    if (StringUtils.hasText(value)) metadata.put(key, value);
  }
}
