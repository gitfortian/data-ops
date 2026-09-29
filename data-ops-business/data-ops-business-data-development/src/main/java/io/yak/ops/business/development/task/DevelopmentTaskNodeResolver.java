package io.yak.ops.business.development.task;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import org.springframework.stereotype.Component;

/** Resolves DevelopmentNode identity and applies lifecycle capability gates. */
@Component
public class DevelopmentTaskNodeResolver {

  private final DevelopmentNodeRepository nodeRepository;

  public DevelopmentTaskNodeResolver(DevelopmentNodeRepository nodeRepository) {
    this.nodeRepository = nodeRepository;
  }

  public DevelopmentNode requireNode(Long nodeId) {
    if (nodeId == null || nodeId <= 0L) {
      throw new IllegalArgumentException("节点 ID 非法");
    }
    return nodeRepository.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
  }

  public DevelopmentNode requireTaskNode(Long nodeId) {
    DevelopmentNode node = requireNode(nodeId);
    node.requireTaskLifecycle();
    return node;
  }
}
