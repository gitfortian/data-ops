package com.dataops.governance.rule.engine;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuleEngineTest {

    @Test
    void shouldExecuteRuleThroughEngine() {
        RuleEvaluator evaluator = context -> new RuleResult(true, null, java.util.List.of());
        RuleEngine engine = new RuleEngine(new RuleExecutor(evaluator));

        RuleResult result = engine.run(new RuleContext("asset-1", "QUALITY", Map.of()));

        assertNotNull(result);
    }
}
