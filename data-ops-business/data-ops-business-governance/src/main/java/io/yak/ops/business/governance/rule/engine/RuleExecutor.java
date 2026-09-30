package io.yak.ops.business.governance.rule.engine;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleResult;

public class RuleExecutor {

    private final RuleEvaluator evaluator;

    public RuleExecutor(RuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public RuleResult execute(RuleContext context) {
        return evaluator.evaluate(context);
    }
}
