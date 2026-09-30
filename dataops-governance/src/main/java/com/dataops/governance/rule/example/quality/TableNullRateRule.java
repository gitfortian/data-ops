package com.dataops.governance.rule.example.quality;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleFinding;
import com.dataops.governance.rule.result.RuleResult;
import com.dataops.governance.rule.result.Severity;
import com.dataops.governance.rule.spi.GovernanceRuleProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Example quality governance rule: table null rate validation.
 */
public class TableNullRateRule implements GovernanceRuleProvider {

    private static final String CODE = "QUALITY_NULL_RATE";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "Table Null Rate Rule";
    }

    @Override
    public RuleResult execute(RuleContext context) {
        Object value = context.getFacts().get("nullRate");
        double nullRate = value instanceof Number ? ((Number) value).doubleValue() : 0D;

        List<RuleFinding> findings = new ArrayList<>();
        boolean passed = nullRate <= 0.05D;

        if (!passed) {
            findings.add(new RuleFinding(
                    context.getAssetId(),
                    "Column null rate exceeds threshold",
                    Severity.HIGH,
                    "Reduce null rate or update data quality policy"
            ));
        }

        return new RuleResult(passed, passed ? Severity.LOW : Severity.HIGH, findings);
    }
}
