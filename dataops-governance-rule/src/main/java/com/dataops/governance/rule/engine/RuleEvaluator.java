package com.dataops.governance.rule.engine;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;

public interface RuleEvaluator {

    RuleResult evaluate(RuleContext context);
}
