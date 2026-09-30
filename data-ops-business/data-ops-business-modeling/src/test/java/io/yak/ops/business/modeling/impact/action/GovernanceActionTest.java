package io.yak.ops.business.modeling.impact.action;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GovernanceActionTest {

    @Test
    void shouldCreateAction() {
        GovernanceAction action = new GovernanceAction();
        action.setActionCode("QUALITY_RECHECK");
        assertEquals("QUALITY_RECHECK", action.getActionCode());
    }
}
