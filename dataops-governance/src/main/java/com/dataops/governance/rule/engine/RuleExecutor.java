package com.dataops.governance.rule.engine;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;

public class RuleExecutor {

    private final RuleEvaluator evaluator;

    public RuleExecutor(RuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public RuleResult execute(RuleContext context) {
        return evaluator.evaluate(context);
    }
}
