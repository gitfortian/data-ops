package io.yak.ops.business.governance.graph.lineage;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;

public class LineageGraphNodeFactory {
    public GovernanceGraphNode createLineageNode(String id, String name) {
        return GovernanceGraphNode.builder()
                .id(id)
                .name(name)
                .type(GovernanceGraphNode.NodeType.DATASET)
                .build();
    }
}
