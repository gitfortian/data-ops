package io.yak.ops.business.mdm.application;

import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.mdm.domain.approval.MdmApprovalStatus;
import io.yak.ops.business.mdm.domain.approval.MdmChange;
import io.yak.ops.business.mdm.domain.clean.CleanJson;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.record.MdmRecordVersion;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmChangeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordVersionRepository;
import io.yak.ops.business.mdm.notification.MdmNotifier;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 变更单终态生效服务(R4,审批中心回调的唯一写入口):
 * 只依赖仓储,不回依赖 ApprovalApi —— 否则会经
 * ApprovalService → ApprovalFlowRegistry → handler 形成 bean 循环
 * (同建模域 ModelPublishApprovalHandler → ModelVersionService 的分层).
 * R6 追加的 {@link MdmNotifier} 同样只向下依赖实体读服务,不构成回边。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MdmChangeEffectService {

  private final MdmChangeRepository changeRepository;
  private final MdmRecordRepository recordRepository;
  private final MdmRecordVersionRepository versionRepository;
  private final BusinessAuditService auditService;
  private final MdmNotifier notifier;

  /** 通过:变更生效(改 mdm_record,version 递增,落版本快照)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmChange applyApproved(Long changeId, String approver, String comment,
      LocalDateTime at) {
    MdmChange change = requirePending(changeId, "审批");
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CHANGE_APPROVE",
                "Approve change request",
                "MDM_CHANGE",
                String.valueOf(changeId),
                change.masterId(),
                "APPLICATION",
                Map.of("approver", String.valueOf(approver))));
    try {
      LocalDateTime now = at == null ? LocalDateTime.now() : at;
      MdmChange approved = change.withApproved(approver, comment, now);
      MdmRecord applied = applyChange(change, approved);
      if (!changeRepository.update(approved)) {
        throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "审批更新失败");
      }
      notifier.changeApplied(change.entityId(), applied, change.id(), approved.approver());
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Change approved",
          Map.of(), "Change approved");
      return approved;
    } catch (RuntimeException exception) {
      audit.failure("MDM_CHANGE_APPROVE_FAILED", exception);
      throw exception;
    }
  }

  /** 拒绝:记录审批意见,变更不生效。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmChange applyRejected(Long changeId, String approver, String comment,
      LocalDateTime at) {
    MdmChange change = requirePending(changeId, "审批");
    AuditOperationHandle audit =
        auditService.start(
            new AuditOperationRequest(
                "MDM_CHANGE_REJECT",
                "Reject change request",
                "MDM_CHANGE",
                String.valueOf(changeId),
                change.masterId(),
                "APPLICATION",
                Map.of("approver", String.valueOf(approver))));
    try {
      MdmChange rejected =
          change.withRejected(approver, comment, at == null ? LocalDateTime.now() : at);
      if (!changeRepository.update(rejected)) {
        throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "审批更新失败");
      }
      AuditTransactions.completeOnCommit(
          audit, AuditEventType.RESOURCE_UPDATED, "Change rejected",
          Map.of(), "Change rejected");
      return rejected;
    } catch (RuntimeException exception) {
      audit.failure("MDM_CHANGE_REJECT_FAILED", exception);
      throw exception;
    }
  }

  /** 撤销(中心回调,仅发起人可触发且由中心校验):申请转 WITHDRAWN。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public MdmChange applyCanceled(Long changeId, String operator, String reason) {
    MdmChange change = requirePending(changeId, "撤回");
    MdmChange withdrawn = change.withWithdrawn();
    if (!changeRepository.update(withdrawn)) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "撤回更新失败");
    }
    log.info("主数据变更 {} 已由 {} 撤回: {}", changeId, operator, reason);
    return withdrawn;
  }

  private MdmChange requirePending(Long changeId, String action) {
    MdmChange change = changeRepository.findById(changeId)
        .orElseThrow(() -> new MdmException(
            MdmErrorCode.APPROVAL_FAILED, "变更不存在: " + changeId));
    if (change.approvalStatus() != MdmApprovalStatus.PENDING) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED,
          "只能" + action + " PENDING 状态变更,当前: " + change.approvalStatus());
    }
    return change;
  }

  /** 审批通过后应用变更到 mdm_record:前后各落一次全量快照,uk 幂等(49009 重试安全)。 */
  private MdmRecord applyChange(MdmChange change, MdmChange approved) {
    return switch (change.changeType()) {
      case UPDATE -> {
        MdmRecord record = requireRecord(change);
        snapshot(record, null, change.applicant());
        MdmRecord updated = record.appliedChange(
            mergeAttributes(record.attributes(), change.changeContent()), MdmRecordStatus.ACTIVE);
        persist(updated, change.id(), approved.approver());
        yield updated;
      }
      case DELETE -> {
        MdmRecord record = requireRecord(change);
        snapshot(record, null, change.applicant());
        MdmRecord deleted =
            record.appliedChange(record.attributes(), MdmRecordStatus.DELETED);
        persist(deleted, change.id(), approved.approver());
        yield deleted;
      }
      case MERGE, CREATE -> throw new MdmException(
          MdmErrorCode.CHANGE_TYPE_UNSUPPORTED,
          "提单校验后不应出现该类型: " + change.changeType());
    };
  }

  private MdmRecord requireRecord(MdmChange change) {
    return recordRepository.findByMasterId(change.entityId(), change.masterId())
        .orElseThrow(() -> new MdmException(
            MdmErrorCode.APPROVAL_FAILED, "记录不存在: " + change.masterId()));
  }

  private void persist(MdmRecord updated, Long changeId, String operator) {
    if (!recordRepository.update(updated)) {
      throw new MdmException(MdmErrorCode.APPROVAL_FAILED, "记录更新失败: " + updated.masterId());
    }
    snapshot(updated, changeId, operator);
  }

  private void snapshot(MdmRecord record, Long changeId, String operator) {
    versionRepository.insertIfAbsent(new MdmRecordVersion(
        null, record.entityId(), record.masterId(), record.version(),
        record.attributes(), record.status(), changeId, operator, null));
  }

  /** 合并变更内容到现有 attributes(JSON patch 模式)。 */
  private String mergeAttributes(String existingAttributes, String changeContent) {
    Map<String, Object> existing = CleanJson.readObject(existingAttributes);
    Map<String, Object> patch = CleanJson.readObject(changeContent);
    existing.putAll(patch);
    return CleanJson.write(existing);
  }
}
