package io.yak.ops.business.approval.api;

import java.time.LocalDateTime;

/** 审批单视图(不含 step 流水;详情走 ApprovalService.ApprovalDetailView)。 */
public record ApprovalInstanceView(
    Long id, String flowCode, String flowName, String bizType, String bizId,
    String title, String payloadJson, String applicant, String status,
    Integer currentLevel, LocalDateTime createTime, LocalDateTime finishTime) {}
