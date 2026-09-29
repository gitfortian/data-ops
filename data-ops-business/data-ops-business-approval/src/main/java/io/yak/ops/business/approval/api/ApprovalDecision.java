package io.yak.ops.business.approval.api;

import java.time.LocalDateTime;

/** 终态回调上下文:单据快照 + 终态成因(approved/canceled 时 comment/lastApprover 可为空)。 */
public record ApprovalDecision(
    Long instanceId, String flowCode, String bizType, String bizId,
    String payloadJson, String applicant, String lastApprover, String comment,
    LocalDateTime at) {}
