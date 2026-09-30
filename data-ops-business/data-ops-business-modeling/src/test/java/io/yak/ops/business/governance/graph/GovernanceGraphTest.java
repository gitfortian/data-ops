package io.yak.ops.business.governance.graph;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphEdge;
import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GovernanceGraphTest {

    @Test
    void shouldCreateGovernanceGraphRelationship() {
        GovernanceGraphNode model = GovernanceGraphNode.builder()
                .id("customer-model")
                .name("Customer")
                .type(GovernanceGraphNode.NodeType.MODEL)
                .build();

        GovernanceGraphEdge edge = GovernanceGraphEdge.builder()
                .sourceNodeId(model.getId())
                .targetNodeId("customer-table")
                .type(GovernanceGraphEdge.EdgeType.DERIVED_FROM)
                .build();

        assertEquals("customer-model", edge.getSourceNodeId());
    }
}
