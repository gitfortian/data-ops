package io.yak.ops.business.governance.rule.engine;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

    @Test
    void shouldExecuteRuleThroughEngine() {
        RuleEvaluator evaluator = context -> new RuleResult(true, null, java.util.List.of());
        RuleEngine engine = new RuleEngine(new RuleExecutor(evaluator));

        RuleResult result = engine.run(new RuleContext("asset-1", "QUALITY", Map.of()));

        assertTrue(result.isPassed());
    }
}
