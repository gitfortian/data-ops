package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.domain.SessionMeta;
import java.util.List;
import java.util.Optional;

/** 会话元数据持久化契约。实现不得向调用方暴露 PO / Mapper 类型。 */
public interface SessionRepository {

  Optional<SessionMeta> findBySessionId(String sessionId);

  void insert(SessionMeta meta);

  List<SessionMeta> listByUser(long userId, long projectId);

  void updateTitle(String sessionId, String title);

  void deleteBySessionId(String sessionId);
}
