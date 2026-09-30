package io.yak.ops.business.governance.graph.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GovernanceGraphNode {
    private String id;
    private String name;
    private NodeType type;

    public enum NodeType {
        MODEL,
        ENTITY,
        ATTRIBUTE,
        DATASET,
        TABLE,
        COLUMN,
        QUALITY_RULE,
        ISSUE,
        ACTION
    }
}
