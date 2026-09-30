package io.yak.ops.business.governance.graph.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GovernanceGraphEdge {
    private String id;
    private String sourceNodeId;
    private String targetNodeId;
    private EdgeType type;

    public enum EdgeType {
        OWNS,
        DEPENDS,
        DERIVED_FROM,
        IMPACTS,
        VIOLATES,
        FIXED_BY
    }
}
