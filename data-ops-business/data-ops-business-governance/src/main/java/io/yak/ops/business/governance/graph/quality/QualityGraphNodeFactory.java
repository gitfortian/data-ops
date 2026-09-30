package io.yak.ops.business.governance.graph.quality;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;

/**
 * Converts quality domain concepts into governance graph nodes.
 */
public class QualityGraphNodeFactory {

    public GovernanceGraphNode createQualityRuleNode(String id, String name) {
        return GovernanceGraphNode.builder()
                .id(id)
                .name(name)
                .type(GovernanceGraphNode.NodeType.QUALITY_RULE)
                .build();
    }
}
