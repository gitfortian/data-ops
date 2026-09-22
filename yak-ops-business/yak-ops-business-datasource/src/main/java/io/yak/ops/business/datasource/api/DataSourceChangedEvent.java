package io.yak.ops.business.datasource.api;

import io.yak.ops.common.enums.datasource.DataSourceDbType;
import java.util.Objects;

/**
 * 数据源生命周期变更的<b>对外契约事件</b>（Ticket 06）：新增、修改、删除都会在事务提交前发布。
 *
 * <p>跨模块订阅姿势：只使用
 * {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)}，勿用普通
 * {@code @EventListener}——后者会在回滚前就收到通知，让下游缓存/快照读到并不存在的状态。
 * 数据源模块自身的 catalog 缓存监听器即为模板实现。
 *
 * <p>事件只携带标识与非敏感描述；连接参数、凭证一律不外泄，订阅方需要明细时按 {@code dataSourceId}
 * 回查数据源查询口。
 */
public record DataSourceChangedEvent(
    Long dataSourceId, DataSourceDbType dbType, String name, ChangeType changeType) {

  public DataSourceChangedEvent {
    if (dataSourceId == null || dataSourceId <= 0L) {
      throw new IllegalArgumentException("dataSourceId must be positive");
    }
    Objects.requireNonNull(changeType, "changeType is required");
  }

  /** 变更类型；订阅方据此区分「首次新增」「配置变更」「已经不存在」。 */
  public enum ChangeType {
    CREATED,
    UPDATED,
    DELETED
  }
}
