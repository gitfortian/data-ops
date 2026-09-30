package io.yak.ops.business.governance.graph.service;

import io.yak.ops.business.governance.graph.domain.GovernanceGraphEdge;
import io.yak.ops.business.governance.graph.domain.GovernanceGraphNode;

import java.util.List;

public interface GovernanceGraphService {
    GovernanceGraphNode saveNode(GovernanceGraphNode node);

    GovernanceGraphEdge saveEdge(GovernanceGraphEdge edge);

    List<GovernanceGraphEdge> findRelations(String nodeId);
}
