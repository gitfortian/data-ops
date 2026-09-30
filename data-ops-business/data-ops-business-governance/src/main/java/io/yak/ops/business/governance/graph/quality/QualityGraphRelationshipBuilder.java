package io.yak.ops.business.governance.graph.quality;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphEdge;

/**
 * Builds quality governance graph relationships.
 */
public class QualityGraphRelationshipBuilder {

    public GovernanceGraphEdge violates(String ruleNodeId, String issueNodeId) {
        return GovernanceGraphEdge.builder()
                .sourceNodeId(ruleNodeId)
                .targetNodeId(issueNodeId)
                .type(GovernanceGraphEdge.EdgeType.VIOLATES)
                .build();
    }
}
