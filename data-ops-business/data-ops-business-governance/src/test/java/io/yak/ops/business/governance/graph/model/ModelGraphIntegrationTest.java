package io.yak.ops.business.governance.graph.model;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphEdge;
import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelGraphIntegrationTest {

    @Test
    void shouldCreateModelGraphRelationship() {
        GovernanceGraphNode node = new ModelGraphNodeFactory()
                .createModelNode("model-001", "CustomerModel");

        GovernanceGraphEdge edge = new ModelGraphRelationshipBuilder()
                .derivedFrom(node.getId(), "dataset-001");

        assertEquals(GovernanceGraphNode.NodeType.MODEL, node.getType());
        assertEquals(GovernanceGraphEdge.EdgeType.DERIVED_FROM, edge.getType());
    }
}
