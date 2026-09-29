package io.yak.ops.business.mdm.domain.subscription;

import java.time.LocalDateTime;

/**
 * 主数据订阅(ticket 59):系统订阅实体变更,变更时通知订阅方。
 * 最小化设计:通知方式 EVENT(审计记录占位)/WEBHOOK(后续增量)。
 */
public record MdmSubscription(
    Long id,
    Long entityId,
    String subscriberCode,
    String subscriberName,
    String notifyMode,
    String status,
    String createdBy,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static final String STATUS_ACTIVE = "ACTIVE";
  public static final String STATUS_DISABLED = "DISABLED";

  /** 站内信:变更发生时向可解析为平台用户的订阅方投递。 */
  public static final String NOTIFY_MODE_EVENT = "EVENT";
  /** 回调推送:依赖外部 HTTP 出口,尚未接入。 */
  public static final String NOTIFY_MODE_WEBHOOK = "WEBHOOK";

  public MdmSubscription withPersisted(Long id, String operator, LocalDateTime now) {
    return new MdmSubscription(
        id, entityId, subscriberCode, subscriberName, notifyMode, status,
        operator, now, now);
  }

  public MdmSubscription withEditable(String subscriberName, String notifyMode) {
    return new MdmSubscription(
        id, entityId, subscriberCode,
        subscriberName == null ? this.subscriberName : subscriberName,
        notifyMode == null ? this.notifyMode : notifyMode,
        status, createdBy, createTime, updateTime);
  }

  public MdmSubscription withStatus(String newStatus) {
    return new MdmSubscription(
        id, entityId, subscriberCode, subscriberName, notifyMode, newStatus,
        createdBy, createTime, updateTime);
  }
}
