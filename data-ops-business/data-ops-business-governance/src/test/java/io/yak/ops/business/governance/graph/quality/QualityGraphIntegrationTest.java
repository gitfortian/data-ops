package io.yak.ops.business.governance.graph.quality;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphEdge;
import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityGraphIntegrationTest {

    @Test
    void shouldCreateQualityGraphRelationship() {
        GovernanceGraphNode node = new QualityGraphNodeFactory()
                .createQualityRuleNode("quality-001", "PhoneNotNull");

        GovernanceGraphEdge edge = new QualityGraphRelationshipBuilder()
                .violates(node.getId(), "issue-001");

        assertEquals(GovernanceGraphNode.NodeType.QUALITY_RULE, node.getType());
        assertEquals(GovernanceGraphEdge.EdgeType.VIOLATES, edge.getType());
    }
}
