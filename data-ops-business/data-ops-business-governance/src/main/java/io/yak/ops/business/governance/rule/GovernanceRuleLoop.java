package io.yak.ops.business.governance.rule;

public class GovernanceRuleLoop {
    public String process(String recommendation) {
        return "governance-rule:" + recommendation;
    }
}
