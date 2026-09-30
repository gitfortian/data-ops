package io.yak.ops.business.governance.graph.model;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphEdge;

/**
 * Builds relationships between model graph nodes.
 */
public class ModelGraphRelationshipBuilder {

    public GovernanceGraphEdge derivedFrom(String modelNodeId, String targetNodeId) {
        return GovernanceGraphEdge.builder()
                .sourceNodeId(modelNodeId)
                .targetNodeId(targetNodeId)
                .type(GovernanceGraphEdge.EdgeType.DERIVED_FROM)
                .build();
    }
}
