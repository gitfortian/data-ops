package io.yak.ops.business.mdm.domain.approval;

import java.time.LocalDateTime;

/**
 * 主数据变更申请(R4 接审批中心):申请单真相在 {@code yak_mdm_change},
 * 两级推进/审批人配置/在途唯一由审批中心实例负责({@code instanceId} 关联);
 * 终态经 {@code MdmChangeApprovalHandler} 回调生效到 mdm_record 并落版本快照。
 */
public record MdmChange(
    Long id,
    Long entityId,
    String masterId,
    MdmChangeType changeType,
    String changeContent,
    int approvalLevel,
    MdmApprovalStatus approvalStatus,
    String applicant,
    String approver,
    String approvalComment,
    LocalDateTime approvalTime,
    Long instanceId,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static final int LEVEL_ONE = 1;
  public static final int LEVEL_TWO = 2;

  public MdmChange withPersisted(Long id, LocalDateTime now) {
    return new MdmChange(
        id, entityId, masterId, changeType, changeContent, approvalLevel,
        approvalStatus, applicant, approver, approvalComment, approvalTime, instanceId, now, now);
  }

  public MdmChange withInstance(Long instanceId) {
    return new MdmChange(
        id, entityId, masterId, changeType, changeContent, approvalLevel,
        approvalStatus, applicant, approver, approvalComment, approvalTime, instanceId,
        createTime, updateTime);
  }

  public MdmChange withApproved(String approver, String comment, LocalDateTime time) {
    return new MdmChange(
        id, entityId, masterId, changeType, changeContent, approvalLevel,
        MdmApprovalStatus.APPROVED, applicant, approver, comment, time, instanceId,
        createTime, updateTime);
  }

  public MdmChange withRejected(String approver, String comment, LocalDateTime time) {
    return new MdmChange(
        id, entityId, masterId, changeType, changeContent, approvalLevel,
        MdmApprovalStatus.REJECTED, applicant, approver, comment, time, instanceId,
        createTime, updateTime);
  }

  public MdmChange withWithdrawn() {
    return new MdmChange(
        id, entityId, masterId, changeType, changeContent, approvalLevel,
        MdmApprovalStatus.WITHDRAWN, applicant, approver, approvalComment, approvalTime,
        instanceId, createTime, updateTime);
  }
}
