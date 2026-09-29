package io.yak.ops.business.resource.domain;

import java.util.List;

/** Read-side tree node; command logic continues to operate on ResourceNode identities. */
public record ResourceTreeNode(ResourceNode resource, List<ResourceTreeNode> children) {

  public ResourceTreeNode {
    children = children == null ? List.of() : List.copyOf(children);
  }
}
