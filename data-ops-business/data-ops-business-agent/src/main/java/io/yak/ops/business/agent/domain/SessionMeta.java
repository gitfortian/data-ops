package io.yak.ops.business.agent.domain;

import java.time.LocalDateTime;

/** 会话元数据：身份 / 归属 / 标题。消息历史真相归 StateStore，本对象不承载对话内容。 */
public record SessionMeta(
    String sessionId,
    long userId,
    long projectId,
    String title,
    LocalDateTime createTime,
    LocalDateTime updateTime) {

  public static SessionMeta create(String sessionId, long userId, long projectId) {
    return new SessionMeta(sessionId, userId, projectId, null, null, null);
  }
}
