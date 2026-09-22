package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PageData;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.clean.CleanJson;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.subscription.MdmSubscription;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSubscriptionRepository;
import io.yak.ops.business.mdm.notification.MdmUserDirectory;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 主数据服务(ticket 59):查询 API + 订阅管理。
 * 最小化设计:查询 API 为 MDM 特有(按 master_id/条件搜索),缓存/API 管理跳 data-service。
 * 订阅一期只走站内信(EVENT):变更生效/合并完成/分发发布时由 MdmNotifier 派发,
 * 收件人即订阅方编码能解析成的平台用户;WEBHOOK 依赖外部 HTTP 出口,尚未接入。
 */
@Service
public class MdmServiceService {

  private static final int MAX_SUBSCRIBER_CODE_LENGTH = 64;
  private static final int MAX_SUBSCRIBER_NAME_LENGTH = 128;

  private final MdmRecordRepository recordRepository;
  private final MdmSubscriptionRepository subscriptionRepository;
  private final MdmEntityService entityService;
  private final BusinessAuditService auditService;
  private final MdmUserDirectory userDirectory;

  public MdmServiceService(
      MdmRecordRepository recordRepository,
      MdmSubscriptionRepository subscriptionRepository,
      MdmEntityService entityService,
      BusinessAuditService auditService,
      MdmUserDirectory userDirectory) {
    this.recordRepository = recordRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.entityService = entityService;
    this.auditService = auditService;
    this.userDirectory = userDirectory;
  }

  // ==== 查询 API ====

  /** 按 master_id 查询单条 ACTIVE 记录。 */
  public RecordView getRecordByMasterId(Long entityId, String masterId) {
    if (!StringUtils.hasText(masterId)) {
      throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "master_id 不能为空");
    }
    MdmRecord record = recordRepository.findByMasterId(entityId, masterId)
        .orElseThrow(() -> new MdmException(MdmErrorCode.RECORD_NOT_FOUND,
            "记录不存在或已合并: " + masterId));
    return toRecordView(record);
  }

  /** 条件搜索:分页,仅 ACTIVE 记录。 */
  public PageData<RecordView> searchRecords(
      Long entityId, String keyword, int pageNo, int pageSize) {
    PageData<MdmRecord> page = recordRepository.page(
        entityId, pageNo, Math.min(pageSize, 200), keyword, MdmRecordStatus.ACTIVE, List.of());
    return page.map(MdmServiceService::toRecordView);
  }

  // ==== 订阅 CRUD ====

  public MdmSubscription getSubscription(Long id) {
    return subscriptionRepository.findById(id)
        .orElseThrow(() -> new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "订阅不存在: " + id));
  }

  public List<MdmSubscription> listSubscriptions(Long entityId) {
    return subscriptionRepository.listByEntity(entityId);
  }

  public long countActiveSubscriptions(Long entityId) {
    return subscriptionRepository.countActiveByEntity(entityId);
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmSubscription createSubscription(
      Long entityId, String subscriberCode, String subscriberName,
      String notifyMode, String operator) {
    entityService.get(entityId);
    String mode = normalizeNotifyMode(notifyMode);
    validateSubscription(entityId, subscriberCode, subscriberName, mode);
    if (subscriptionRepository.existsBySubscriber(entityId, subscriberCode, null)) {
      throw new MdmException(
          MdmErrorCode.RECORD_NOT_FOUND,
          "该实体已存在相同订阅方: " + subscriberCode);
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_SUBSCRIPTION_CREATE",
                "Create subscription",
                "MDM_SUBSCRIPTION",
                null,
                subscriberCode,
                "APPLICATION",
                Map.of("entityId", String.valueOf(entityId))));
    try {
      MdmSubscription inserted = subscriptionRepository.insert(
          new MdmSubscription(
              null, entityId, subscriberCode,
              subscriberName == null ? "" : subscriberName,
              mode,
              MdmSubscription.STATUS_ACTIVE,
              operator, null, null),
          operator);
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_CREATED, "Subscription created",
          Map.of(), "Subscription created");
      return inserted;
    } catch (RuntimeException exception) {
      audit.failure("MDM_SUBSCRIPTION_CREATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void updateSubscription(
      Long id, String subscriberName, String notifyMode) {
    MdmSubscription existing = getSubscription(id);
    String mode = StringUtils.hasText(notifyMode) ? normalizeNotifyMode(notifyMode) : null;
    if (mode != null) {
      validateNotifyMode(mode);
    }
    MdmSubscription updated = existing.withEditable(subscriberName, mode);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_SUBSCRIPTION_UPDATE",
                "Update subscription",
                "MDM_SUBSCRIPTION",
                String.valueOf(id),
                existing.subscriberCode(),
                "APPLICATION",
                Map.of("entityId", String.valueOf(existing.entityId()))));
    try {
      if (!subscriptionRepository.update(updated)) {
        throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "订阅更新失败");
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Subscription updated",
          Map.of(), "Subscription updated");
    } catch (RuntimeException exception) {
      audit.failure("MDM_SUBSCRIPTION_UPDATE_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void setSubscriptionStatus(Long id, String status) {
    MdmSubscription existing = getSubscription(id);
    if (!MdmSubscription.STATUS_ACTIVE.equals(status)
        && !MdmSubscription.STATUS_DISABLED.equals(status)) {
      throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND,
          "状态不合法: " + status + "，仅支持 ACTIVE/DISABLED");
    }
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_SUBSCRIPTION_STATUS",
                "Toggle subscription status",
                "MDM_SUBSCRIPTION",
                String.valueOf(id),
                existing.subscriberCode(),
                "APPLICATION",
                Map.of("status", status)));
    try {
      if (!subscriptionRepository.update(existing.withStatus(status))) {
        throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "订阅状态更新失败");
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Subscription status changed",
          Map.of(), "Subscription status changed");
    } catch (RuntimeException exception) {
      audit.failure("MDM_SUBSCRIPTION_STATUS_FAILED", exception);
      throw exception;
    }
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void deleteSubscription(Long id) {
    MdmSubscription existing = getSubscription(id);
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_SUBSCRIPTION_DELETE",
                "Delete subscription",
                "MDM_SUBSCRIPTION",
                String.valueOf(id),
                existing.subscriberCode(),
                "APPLICATION",
                Map.of("entityId", String.valueOf(existing.entityId()))));
    try {
      if (!subscriptionRepository.deleteById(id)) {
        throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "订阅删除失败");
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_DELETED, "Subscription deleted",
          Map.of(), "Subscription deleted");
    } catch (RuntimeException exception) {
      audit.failure("MDM_SUBSCRIPTION_DELETE_FAILED", exception);
      throw exception;
    }
  }

  // ==== 校验 ====

  private void validateSubscription(
      Long entityId, String subscriberCode, String subscriberName, String notifyMode) {
    if (!StringUtils.hasText(subscriberCode)
        || subscriberCode.length() > MAX_SUBSCRIBER_CODE_LENGTH) {
      throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "订阅方编码必填且不超过 64 字符");
    }
    if (subscriberName != null && subscriberName.length() > MAX_SUBSCRIBER_NAME_LENGTH) {
      throw new MdmException(MdmErrorCode.RECORD_NOT_FOUND, "订阅方名称不超过 128 字符");
    }
    validateNotifyMode(notifyMode);
  }

  /**
   * 一期只接通站内信:WEBHOOK 依赖外部 HTTP 出口,登记成功却永不推送比直接报错更糟,
   * 所以在入口就拒掉而不是静默收下。
   */
  private void validateNotifyMode(String notifyMode) {
    if (!MdmSubscription.NOTIFY_MODE_EVENT.equals(notifyMode)) {
      throw new MdmException(MdmErrorCode.NOTIFY_MODE_UNSUPPORTED, notifyMode);
    }
  }

  /** 归一通知方式:空视为默认 EVENT,大小写/首尾空白差异不该让订阅分档失效。 */
  private static String normalizeNotifyMode(String notifyMode) {
    return StringUtils.hasText(notifyMode)
        ? notifyMode.trim().toUpperCase(Locale.ROOT)
        : MdmSubscription.NOTIFY_MODE_EVENT;
  }

  /** 订阅方编码是否解析得到平台用户——false 时站内信发不到人,页面需显式提示。 */
  public boolean subscriberReachable(String subscriberCode) {
    return userDirectory.userIdOf(subscriberCode).isPresent();
  }

  private static RecordView toRecordView(MdmRecord record) {
    return new RecordView(
        record.masterId(),
        record.status() == null ? null : record.status().name(),
        record.version(),
        CleanJson.readObject(record.attributes()),
        CleanJson.readObject(record.sourceIds()));
  }

  // ==== DTO ====

  public record RecordView(
      String masterId, String status, int version,
      Map<String, Object> attributes, Map<String, Object> sourceIds) {}
}
