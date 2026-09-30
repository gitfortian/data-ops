package io.yak.ops.business.modeling.impact.rule;

/**
 * Impact analysis rule extension contract.
 *
 * Rules evaluate impact context and return governance-oriented results.
 */
public interface ImpactRule {

    String ruleCode();

    ImpactRuleResult evaluate(ImpactRuleContext context);
}
