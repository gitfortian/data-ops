package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;

import io.yak.ops.business.agent.dao.mapper.AgentSessionMapper;
import io.yak.ops.business.agent.dao.model.AgentSessionPO;
import io.yak.ops.business.agent.domain.SessionMeta;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 会话元数据持久化适配。 */
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class SessionRepositoryAdapter implements SessionRepository {

  private final AgentSessionMapper mapper;

  @Override
  public Optional<SessionMeta> findBySessionId(String sessionId) {
    return Optional.ofNullable(
            mapper.selectOne(new LambdaQueryWrapper<AgentSessionPO>().eq(AgentSessionPO::getSessionId, sessionId)))
        .map(this::toDomain);
  }

  @Override
  public void insert(SessionMeta meta) {
    AgentSessionPO po = new AgentSessionPO();
    po.setSessionId(meta.sessionId());
    po.setUserId(meta.userId());
    po.setProjectId(meta.projectId());
    po.setTitle(meta.title());
    mapper.insert(po);
  }

  @Override
  public List<SessionMeta> listByUser(long userId, long projectId) {
    return mapper.selectList(
            new LambdaQueryWrapper<AgentSessionPO>()
                .eq(AgentSessionPO::getUserId, userId)
                .eq(AgentSessionPO::getProjectId, projectId)
                .orderByDesc(AgentSessionPO::getUpdateTime))
        .stream()
        .map(this::toDomain)
        .toList();
  }

  @Override
  public void updateTitle(String sessionId, String title) {
    AgentSessionPO po = new AgentSessionPO();
    po.setTitle(title);
    mapper.update(po, new LambdaQueryWrapper<AgentSessionPO>().eq(AgentSessionPO::getSessionId, sessionId));
  }

  @Override
  public void deleteBySessionId(String sessionId) {
    mapper.delete(new LambdaQueryWrapper<AgentSessionPO>().eq(AgentSessionPO::getSessionId, sessionId));
  }

  private SessionMeta toDomain(AgentSessionPO po) {
    return new SessionMeta(
        po.getSessionId(), po.getUserId(), po.getProjectId() == null ? 0L : po.getProjectId(),
        po.getTitle(), po.getCreateTime(), po.getUpdateTime());
  }
}
