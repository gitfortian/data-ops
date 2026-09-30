package io.yak.ops.business.governance.workflow;

public class GovernanceWorkflowTest {

    public void shouldCreateGovernanceDecision() {
        GovernanceDecisionEngine engine = new GovernanceDecisionEngine();
        assert engine.decide("HIGH").equals("HIGH");
    }
}
