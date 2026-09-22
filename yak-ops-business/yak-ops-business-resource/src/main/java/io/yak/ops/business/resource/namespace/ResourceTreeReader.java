package io.yak.ops.business.resource.namespace;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.domain.ResourceTreeNode;
import io.yak.ops.business.resource.repository.ResourceRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds the resource namespace tree from durable metadata. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceTreeReader {

  private final ResourceRepository repository;

  public List<ResourceTreeNode> tree() {
    List<ResourceNode> resources = repository.findAll();
    Map<Long, MutableTreeNode> mapped = new LinkedHashMap<>();
    for (ResourceNode resource : resources) {
      mapped.put(resource.getId(), new MutableTreeNode(resource));
    }

    List<MutableTreeNode> roots = new ArrayList<>();
    for (ResourceNode resource : resources) {
      MutableTreeNode current = mapped.get(resource.getId());
      if (resource.getParentId() == null || resource.getParentId() == 0L) {
        roots.add(current);
        continue;
      }
      MutableTreeNode parent = mapped.get(resource.getParentId());
      if (parent == null) {
        roots.add(current);
      } else {
        parent.children.add(current);
      }
    }
    return roots.stream().map(MutableTreeNode::freeze).toList();
  }

  private static final class MutableTreeNode {
    private final ResourceNode resource;
    private final List<MutableTreeNode> children = new ArrayList<>();

    private MutableTreeNode(ResourceNode resource) {
      this.resource = resource;
    }

    private ResourceTreeNode freeze() {
      return new ResourceTreeNode(resource, children.stream().map(MutableTreeNode::freeze).toList());
    }
  }
}
