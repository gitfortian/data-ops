package com.dataops.governance.rule.example.security;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleFinding;
import com.dataops.governance.rule.result.RuleResult;
import com.dataops.governance.rule.result.Severity;
import com.dataops.governance.rule.spi.GovernanceRuleProvider;

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
