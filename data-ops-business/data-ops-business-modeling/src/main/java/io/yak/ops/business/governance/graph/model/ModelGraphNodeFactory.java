package io.yak.ops.business.governance.graph.model;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;

/**
 * Converts model domain concepts into governance graph nodes.
 */
public class ModelGraphNodeFactory {

    public GovernanceGraphNode createModelNode(String id, String name) {
        return GovernanceGraphNode.builder()
                .id(id)
                .name(name)
                .type(GovernanceGraphNode.NodeType.MODEL)
                .build();
    }
}
