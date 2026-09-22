package io.yak.ops.business.asset.schedule;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.SchedulePolicy;
import io.yak.framework.schedule.api.ScheduleTarget;
import io.yak.framework.schedule.api.ScheduleTrigger;
import io.yak.ops.common.schedule.YakScheduleGateway;
import io.yak.ops.common.schedule.YakScheduleNamespaces;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 资产闹钟适配层(ticket 98):每项目三个固定 cron——对账 02:00、健康度全量重算 03:00、
 * 概览快照 04:00。幂等登记,调度器未装配时静默跳过(与 lifecycle 同款)。
 */
@Slf4j
@Component
public class AssetScheduleEngineBridge {

  static final String NAMESPACE = YakScheduleNamespaces.DATA_ASSET;
  static final String HANDLER = "assetScheduleHandler";
  static final String TASK_RECONCILE = "RECONCILE";
  static final String TASK_HEALTH = "HEALTH";
  static final String TASK_SNAPSHOT = "SNAPSHOT";

  private final YakScheduleGateway gateway;

  public AssetScheduleEngineBridge(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
  }

  public boolean available() {
    return gateway.available();
  }

  /** 幂等登记项目三个闹钟(手动对账时惰性补登)。 */
  public void ensureProjectAlarms(long projectId) {
    if (!gateway.available()) {
      return;
    }
    ensure(projectId, TASK_RECONCILE, "0 0 2 * * ?", "资产对账闹钟");
    ensure(projectId, TASK_HEALTH, "0 0 3 * * ?", "健康度重算闹钟");
    ensure(projectId, TASK_SNAPSHOT, "0 0 4 * * ?", "资产概览快照闹钟");
  }

  private void ensure(long projectId, String task, String cron, String name) {
    String scheduleName = task.toLowerCase() + "-project-" + projectId;
    if (gateway.snapshot(scheduleName).isPresent()) {
      return;
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("projectId", projectId);
    payload.put("task", task);
    Map<String, String> metadata = Map.of(
        "source", "yak-ops", "projectId", String.valueOf(projectId), "task", task);
    try {
      gateway.save(new ScheduleDefinition(
          gateway.key(scheduleName),
          name + "(project " + projectId + ")",
          ScheduleTrigger.cron(cron, ZoneId.systemDefault()),
          new ScheduleTarget(HANDLER, payload),
          SchedulePolicy.defaults(),
          true,
          metadata));
    } catch (RuntimeException e) {
      log.warn("asset alarm registration failed, task={} project={}", task, projectId, e);
    }
  }
}
