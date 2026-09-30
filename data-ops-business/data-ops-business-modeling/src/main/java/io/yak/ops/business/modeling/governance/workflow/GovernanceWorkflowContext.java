package io.yak.ops.business.modeling.governance.workflow;

import lombok.Data;

@Data
public class GovernanceWorkflowContext {
    private String impactId;
    private String taskId;
    private String operator;
}
