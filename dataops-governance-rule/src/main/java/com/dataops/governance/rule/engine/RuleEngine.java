package com.dataops.governance.rule.engine;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;

public class RuleEngine {

    private final RuleExecutor executor;

    public RuleEngine(RuleExecutor executor) {
        this.executor = executor;
    }

    public RuleResult run(RuleContext context) {
        return executor.execute(context);
    }
}
