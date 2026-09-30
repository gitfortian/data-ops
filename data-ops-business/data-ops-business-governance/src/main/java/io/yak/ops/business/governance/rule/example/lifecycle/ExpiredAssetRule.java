package io.yak.ops.business.governance.rule.example.lifecycle;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleFinding;
import io.yak.ops.business.governance.rule.result.RuleResult;
import io.yak.ops.business.governance.rule.result.Severity;
import io.yak.ops.business.governance.rule.spi.GovernanceRuleProvider;

import java.util.ArrayList;

public class ExpiredAssetRule implements GovernanceRuleProvider {

    @Override
    public String code() {
        return "LIFECYCLE_EXPIRED_ASSET";
    }

    @Override
    public String name() {
        return "Expired Asset Lifecycle Rule";
    }

    @Override
    public RuleResult execute(RuleContext context) {
        Object expired = context.getFacts().get("expired");
        boolean passed = !Boolean.TRUE.equals(expired);
        if (passed) {
            return new RuleResult(true, Severity.LOW, new ArrayList<>());
        }
        return new RuleResult(false, Severity.MEDIUM, java.util.List.of(
                new RuleFinding(context.getAssetId(), "Asset exceeded lifecycle retention period", Severity.MEDIUM, "Archive or review asset lifecycle")
        ));
    }
}
