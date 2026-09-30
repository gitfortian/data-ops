package io.yak.ops.business.modeling.governance.workflow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GovernanceWorkflowServiceTest {
    @Test
    void shouldCreateWorkflowTask() {
        GovernanceWorkflowContext context = new GovernanceWorkflowService().createTask("impact-1");
        assertEquals("impact-1", context.getImpactId());
    }
}
