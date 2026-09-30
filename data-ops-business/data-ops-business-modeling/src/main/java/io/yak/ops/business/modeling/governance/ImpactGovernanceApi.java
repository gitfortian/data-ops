package io.yak.ops.business.modeling.governance;

/**
 * Impact Governance API abstraction.
 * Provides governance entry points for impact analysis results.
 */
public interface ImpactGovernanceApi {

    GovernanceResult validate(String impactId);

    record GovernanceResult(boolean passed, String message) {
    }
}
