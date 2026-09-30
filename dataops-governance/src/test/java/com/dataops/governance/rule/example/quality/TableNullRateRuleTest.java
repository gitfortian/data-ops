package com.dataops.governance.rule.example.quality;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableNullRateRuleTest {

    @Test
    void shouldPassWhenNullRateBelowThreshold() {
        TableNullRateRule rule = new TableNullRateRule();
        RuleContext context = new RuleContext("asset-001", "quality", Map.of("nullRate", 0.01));

        RuleResult result = rule.execute(context);

        assertTrue(result.isPassed());
    }

    @Test
    void shouldFailWhenNullRateExceedsThreshold() {
        TableNullRateRule rule = new TableNullRateRule();
        RuleContext context = new RuleContext("asset-001", "quality", Map.of("nullRate", 0.12));

        RuleResult result = rule.execute(context);

        assertFalse(result.isPassed());
    }
}
