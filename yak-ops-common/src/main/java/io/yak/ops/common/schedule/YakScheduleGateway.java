package io.yak.ops.common.schedule;

import io.yak.framework.schedule.api.ScheduleDefinition;
import io.yak.framework.schedule.api.ScheduleKey;
import io.yak.framework.schedule.api.ScheduleManager;
import io.yak.framework.schedule.api.ScheduleSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Yak Ops 业务模块访问 Yak Schedule 的轻量网关。
 *
 * <p>这里只统一引擎操作语义，不承载任何业务状态、重试、并发准入或运行记录。</p>
 */
public final class YakScheduleGateway {
  private final Supplier<ScheduleManager> managerSupplier;
  private final String namespace;

  public YakScheduleGateway(Supplier<ScheduleManager> managerSupplier, String namespace) {
    if (managerSupplier == null) throw new IllegalArgumentException("ScheduleManager supplier 不能为空");
    if (namespace == null || namespace.isBlank()) throw new IllegalArgumentException("调度 namespace 不能为空");
    this.managerSupplier = managerSupplier;
    this.namespace = namespace.trim();
  }

  public String namespace() {
    return namespace;
  }

  public boolean available() {
    return current() != null;
  }

  public ScheduleSnapshot save(ScheduleDefinition definition) {
    if (definition == null) throw new IllegalArgumentException("调度定义不能为空");
    if (!namespace.equals(definition.key().namespace())) {
      throw new IllegalArgumentException(
          "调度定义 namespace 不匹配，expected=" + namespace + ", actual=" + definition.key().namespace());
    }
    return required().save(definition);
  }

  public Optional<ScheduleSnapshot> snapshot(String name) {
    ScheduleManager manager = current();
    return manager == null ? Optional.empty() : manager.get(key(name));
  }

  public List<ScheduleSnapshot> list() {
    ScheduleManager manager = current();
    return manager == null ? List.of() : manager.list(namespace);
  }

  public void pauseIfPresent(String name) {
    ScheduleManager manager = current();
    if (manager == null) return;
    ScheduleKey key = key(name);
    if (manager.get(key).isPresent()) manager.pause(key);
  }

  public void resumeIfPresent(String name) {
    ScheduleManager manager = current();
    if (manager == null) return;
    ScheduleKey key = key(name);
    if (manager.get(key).isPresent()) manager.resume(key);
  }

  public void deleteIfPresent(String name) {
    ScheduleManager manager = current();
    if (manager == null) return;
    ScheduleKey key = key(name);
    if (manager.get(key).isPresent()) manager.delete(key);
  }

  public boolean runNowIfPresent(String name) {
    ScheduleManager manager = current();
    if (manager == null) return false;
    ScheduleKey key = key(name);
    if (manager.get(key).isEmpty()) return false;
    manager.runNow(key);
    return true;
  }

  public ScheduleKey key(String name) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("调度业务 ID 不能为空");
    return new ScheduleKey(namespace, name.trim());
  }

  private ScheduleManager current() {
    return managerSupplier.get();
  }

  private ScheduleManager required() {
    ScheduleManager manager = current();
    if (manager == null) {
      throw new IllegalStateException(
          "Yak ScheduleManager 不可用，请确认 yak-schedule-core、调度引擎插件与 yak.schedule.enabled 配置");
    }
    return manager;
  }
}
