package io.yak.ops.business.governance.rule.engine;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleResult;

public interface RuleEvaluator {

    RuleResult evaluate(RuleContext context);
}
