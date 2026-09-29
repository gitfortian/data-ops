package io.yak.ops.business.approval.api;

/** 发起审批命令;applicant 必须由服务端可信上下文填入,不接受前端传值。 */
public record ApprovalSubmitCommand(
    String flowCode, String bizType, String bizId, String title,
    String payloadJson, String applicant) {}
