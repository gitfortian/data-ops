package io.yak.ops.business.modeling.governance.workflow;

public class GovernanceWorkflowService {

    public GovernanceWorkflowContext createTask(String impactId) {
        GovernanceWorkflowContext context = new GovernanceWorkflowContext();
        context.setImpactId(impactId);
        return context;
    }
}
