package io.yak.ops.business.agent.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.agent.config.ConditionalOnAgentEnabled;
import io.yak.ops.business.agent.dao.mapper.AgentMessageMapper;
import io.yak.ops.business.agent.dao.model.AgentMessagePO;
import io.yak.ops.business.agent.domain.MessageTreeNode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/** 消息树持久化适配。PO↔domain 转换只发生在本持久化边界，接口不泄漏 MyBatis 类型。 */
@Slf4j
@ConditionalOnAgentEnabled
@Repository
@RequiredArgsConstructor
public class MessageTreeRepositoryAdapter implements MessageTreeRepository {

  private final AgentMessageMapper mapper;

  @Override
  public void append(MessageTreeNode node) {
    try {
      AgentMessagePO po = toPo(node);
      if (po.getParentId() == null) {
        findCurrentLeaf(node.sessionId())
            .ifPresent(leaf -> po.setParentId(leaf.messageId()));
      }
      mapper.insert(po);
    } catch (Exception e) {
      log.warn("failed to append agent message node: sessionId={}, messageId={}",
          node.sessionId(), node.messageId(), e);
    }
  }

  @Override
  public boolean complete(
      String sessionId, String messageId, String content, Long totalTokens) {
    try {
      AgentMessagePO po = findByMessageId(sessionId, messageId);
      if (po == null) {
        return false;
      }
      if (content != null) {
        po.setContent(content);
      }
      po.setTotalTokens(totalTokens);
      po.setDone(1);
      return mapper.updateById(po) > 0;
    } catch (Exception e) {
      log.warn("failed to complete agent message: sessionId={}, messageId={}",
          sessionId, messageId, e);
      return false;
    }
  }

  @Override
  public Optional<MessageTreeNode> findCurrentLeaf(String sessionId) {
    List<AgentMessagePO> nodes = mapper.selectList(
        new LambdaQueryWrapper<AgentMessagePO>()
            .eq(AgentMessagePO::getSessionId, sessionId)
            .eq(AgentMessagePO::getIsDeleted, 0)
            .eq(AgentMessagePO::getDone, 1)
            .orderByDesc(AgentMessagePO::getCreateTime)
            .last("LIMIT 1"));
    return nodes.stream().findFirst().map(MessageTreeRepositoryAdapter::toDomain);
  }

  @Override
  public List<MessageTreeNode> listBySession(String sessionId) {
    return mapper.selectList(new LambdaQueryWrapper<AgentMessagePO>()
            .eq(AgentMessagePO::getSessionId, sessionId)
            .eq(AgentMessagePO::getIsDeleted, 0)
            .orderByAsc(AgentMessagePO::getCreateTime))
        .stream()
        .map(MessageTreeRepositoryAdapter::toDomain)
        .toList();
  }

  private static AgentMessagePO toPo(MessageTreeNode node) {
    AgentMessagePO po = new AgentMessagePO();
    po.setSessionId(node.sessionId());
    po.setMessageId(node.messageId());
    po.setParentId(node.parentId());
    po.setRole(node.role());
    po.setContent(node.content());
    po.setModelName(node.modelName());
    po.setTotalTokens(node.totalTokens());
    po.setDone(node.done() ? 1 : 0);
    po.setIsDeleted(0);
    po.setCreateTime(node.createTime());
    po.setUpdateTime(node.updateTime());
    return po;
  }

  private static MessageTreeNode toDomain(AgentMessagePO po) {
    return new MessageTreeNode(
        po.getSessionId(),
        po.getMessageId(),
        po.getParentId(),
        po.getRole(),
        po.getContent(),
        po.getModelName(),
        po.getTotalTokens(),
        po.getDone() != null && po.getDone() == 1,
        po.getCreateTime(),
        po.getUpdateTime());
  }

  private AgentMessagePO findByMessageId(String sessionId, String messageId) {
    var list = mapper.selectList(new LambdaQueryWrapper<AgentMessagePO>()
        .eq(AgentMessagePO::getSessionId, sessionId)
        .eq(AgentMessagePO::getMessageId, messageId)
        .eq(AgentMessagePO::getIsDeleted, 0));
    return list.isEmpty() ? null : list.get(0);
  }
}