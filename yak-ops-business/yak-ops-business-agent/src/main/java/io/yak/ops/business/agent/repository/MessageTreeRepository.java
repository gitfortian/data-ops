package io.yak.ops.business.agent.repository;

import io.yak.ops.business.agent.domain.MessageTreeNode;
import java.util.List;
import java.util.Optional;

/**
 * 消息树持久化契约（消息模型 v2）：树状结构支撑编辑分叉 / regenerate / 分支导航。
 * 双轨存储的另一半：对话真相仍在 AgentStateStore，本表承载产品化交互所需的树结构。
 *
 * <p>签名只暴露 {@link MessageTreeNode} 领域值对象，不泄漏 MyBatis PO
 * （DEPENDENCIES §8 规则 1）；PO 转换由 adapter 在持久化边界内完成。</p>
 */
public interface MessageTreeRepository {

  /** 追加一条消息节点（parentId 为空时自动挂到当前叶子）。 */
  void append(MessageTreeNode node);

  /** 覆写内容并置为终态（流式完成/停止/失败统一走这里收口）。 */
  boolean complete(String sessionId, String messageId, String content, Long totalTokens);

  /** 当前活跃叶子（最新终态节点）；无消息返回 empty。 */
  Optional<MessageTreeNode> findCurrentLeaf(String sessionId);

  /** 按创建序取全部分支节点（树重建输入）。 */
  List<MessageTreeNode> listBySession(String sessionId);
}