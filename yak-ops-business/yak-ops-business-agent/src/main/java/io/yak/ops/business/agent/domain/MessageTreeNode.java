package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;

/**
 * 消息树节点投影（读模型/命令入参共用）。truth owner 是 {@code yak_agent_message} 表；
 * 软删(isDeleted)是持久化存储细节，不进本值对象——Repository contract 不暴露 MyBatis PO
 * （DEPENDENCIES §8 规则 1）。parentId 为 null 表示根节点（挂到当前叶子由 adapter 回填）。
 */
public record MessageTreeNode(
    String sessionId,
    String messageId,
    String parentId,
    String role,
    String content,
    String modelName,
    Long totalTokens,
    boolean done,
    LocalDateTime createTime,
    LocalDateTime updateTime) {}