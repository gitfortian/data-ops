package io.yak.ops.business.agent.conversation;

import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.domain.SessionMeta;
import io.yak.ops.business.agent.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 会话归属校验与首访绑定（先校验后读写的唯一执行点）。
 *
 * <p>历史版本还持有进程内单飞互斥锁（TTL 兜底）；提交/执行分离后单飞真相收敛到
 * {@code yak_agent_turn} DB 状态机（提交侧活跃校验 + 认领条件 UPDATE），进程内锁因
 * 违反 Truth 单一 owner 原则被移除。横向多节点部署时若出现跨实例并发窗口，
 * 再引入分布式 TurnGate 作为届时的前置事项。</p>
 */
@ConditionalOnAgentEnabled
@Component
@RequiredArgsConstructor
public class AgentSessionOwnerValidator {

  /** 会话初始标题最大长度（截取首问开头部分）。 */
  private static final int TITLE_MAX_LENGTH = 30;

  private final SessionRepository sessionRepository;

  /** 首访绑定归属；已存在则严格校验归属人，不匹配直接拒绝。 */
  public void ensureOwner(String sessionId, long userId) {
    ensureOwner(sessionId, userId, 0L, null);
  }

  /** 首访绑定归属；titleHint 作为初始标题（仅创建时生效，不覆盖后续重命名）。 */
  public void ensureOwner(String sessionId, long userId, String titleHint) {
    ensureOwner(sessionId, userId, 0L, titleHint);
  }

  /** 首访绑定归属并关联项目空间；projectId 为项目隔离恢复通道。 */
  public void ensureOwner(String sessionId, long userId, long projectId, String titleHint) {
    var existing = sessionRepository.findBySessionId(sessionId);
    if (existing.isEmpty()) {
      sessionRepository.insert(
          new SessionMeta(sessionId, userId, projectId, initialTitle(titleHint), null, null));
      return;
    }
    assertOwner(sessionId, userId);
  }

  /** 仅校验不绑定：用于删除等不允许隐式创建的命令。 */
  public void assertOwner(String sessionId, long userId) {
    var existing = sessionRepository.findBySessionId(sessionId);
    if (existing.isEmpty() || existing.get().userId() != userId) {
      throw new IllegalArgumentException("会话不存在或不属于当前用户");
    }
  }

  /** 标题取对话开头部分：换行折叠为空格，超长截断。 */
  private static String initialTitle(String titleHint) {
    if (titleHint == null) {
      return null;
    }
    String normalized = titleHint.replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.length() <= TITLE_MAX_LENGTH
        ? normalized
        : normalized.substring(0, TITLE_MAX_LENGTH) + "…";
  }
}
