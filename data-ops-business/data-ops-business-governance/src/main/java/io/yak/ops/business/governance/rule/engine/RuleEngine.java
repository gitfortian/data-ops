package io.yak.ops.business.governance.rule.engine;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleResult;

public class RuleEngine {

    private final RuleExecutor executor;

    public RuleEngine(RuleExecutor executor) {
        this.executor = executor;
    }

    public RuleResult run(RuleContext context) {
        return executor.execute(context);
    }
}
