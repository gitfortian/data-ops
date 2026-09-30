package io.yak.ops.business.governance.rule.spi;

import io.yak.ops.business.governance.rule.context.RuleContext;
import io.yak.ops.business.governance.rule.result.RuleResult;

/**
 * Extension point for domain governance rules.
 */
public interface GovernanceRuleProvider {

    String code();

    String name();

    RuleResult execute(RuleContext context);
}
