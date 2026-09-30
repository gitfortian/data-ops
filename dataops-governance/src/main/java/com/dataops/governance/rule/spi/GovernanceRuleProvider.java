package com.dataops.governance.rule.spi;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;

/**
 * Extension point for domain governance rules.
 */
public interface GovernanceRuleProvider {

    String code();

    String name();

    RuleResult execute(RuleContext context);
}
