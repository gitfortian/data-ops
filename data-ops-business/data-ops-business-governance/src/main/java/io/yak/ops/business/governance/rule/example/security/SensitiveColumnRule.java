package io.yak.ops.business.governance.rule.example.security;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleFinding;
import io.yak.ops.business.governance.rule.result.RuleResult;
import io.yak.ops.business.governance.rule.result.Severity;
import io.yak.ops.business.governance.rule.spi.GovernanceRuleProvider;

import java.util.ArrayList;

public class SensitiveColumnRule implements GovernanceRuleProvider {

    @Override
    public String code() {
        return "SECURITY_SENSITIVE_COLUMN";
    }

    @Override
    public String name() {
        return "Sensitive Column Protection Rule";
    }

    @Override
    public RuleResult execute(RuleContext context) {
        Object protectedFlag = context.getFacts().get("protected");
        boolean passed = Boolean.TRUE.equals(protectedFlag);
        if (passed) {
            return new RuleResult(true, Severity.LOW, new ArrayList<>());
        }
        return new RuleResult(false, Severity.HIGH, java.util.List.of(
                new RuleFinding(context.getAssetId(), "Sensitive column has no protection", Severity.HIGH, "Enable masking policy")
        ));
    }
}
