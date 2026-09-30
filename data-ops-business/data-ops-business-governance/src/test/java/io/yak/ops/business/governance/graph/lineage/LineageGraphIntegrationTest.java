package io.yak.ops.business.governance.graph.lineage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LineageGraphIntegrationTest {
    @Test
    void shouldCreateLineageGraphNode() {
        var node = new LineageGraphNodeFactory().createLineageNode("dataset-001", "CustomerDataset");
        assertEquals("dataset-001", node.getId());
    }
}
