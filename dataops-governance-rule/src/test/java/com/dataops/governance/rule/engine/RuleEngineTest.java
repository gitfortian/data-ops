package com.dataops.governance.rule.engine;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    @Test
    void shouldExecuteRuleEvaluatorThroughEngine() {
        RuleEvaluator evaluator = context -> new RuleResult(true, null, java.util.Collections.emptyList());

        RuleEngine engine = new RuleEngine(
                new RuleExecutor(evaluator)
        );

        RuleResult result = engine.run(new RuleContext());

        assertTrue(result.isPassed());
    }
}
