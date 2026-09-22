package io.yak.ops.business.lifecycle.schedule;

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
 * 生命周期闹钟适配层:每项目两个固定 cron——失败重试(30 分钟)与存储快照(每日 02:00)。
 * D7:Quartz 内存存储会丢闹钟,业务表(yak_lc_dispatch_record)才是重试事实源,
 * 这里只负责"到点来看一眼"。
 */
@Slf4j
@Component
public class LifecycleScheduleEngineBridge {

  static final String NAMESPACE = YakScheduleNamespaces.DATA_LIFECYCLE;
  static final String HANDLER = "lifecycleTtlScheduleHandler";
  static final String TASK_RETRY = "RETRY";
  static final String TASK_SNAPSHOT = "SNAPSHOT";

  private final YakScheduleGateway gateway;

  public LifecycleScheduleEngineBridge(ObjectProvider<ScheduleManager> scheduleManagers) {
    this.gateway = new YakScheduleGateway(scheduleManagers::getIfAvailable, NAMESPACE);
  }

  public boolean available() {
    return gateway.available();
  }

  /** 幂等登记项目两个闹钟(策略写入路径调用;调度器未装配时静默跳过)。 */
  public void ensureProjectAlarms(long projectId) {
    if (!gateway.available()) {
      return;
    }
    ensure(projectId, TASK_RETRY, "0 0/30 * * * ?", "TTL 失败重试闹钟");
    ensure(projectId, TASK_SNAPSHOT, "0 0 2 * * ?", "存储快照采集闹钟");
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
      log.warn("lifecycle alarm registration failed, task={} project={}", task, projectId, e);
    }
  }
}
