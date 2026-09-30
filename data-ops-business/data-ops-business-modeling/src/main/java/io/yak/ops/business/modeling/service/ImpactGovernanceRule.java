package io.yak.ops.business.modeling.service;

/**
 * Governance rule abstraction for impact analysis.
 */
public interface ImpactGovernanceRule {

    boolean support(String objectType);

    default boolean validate(Long objectId) {
        return objectId != null;
    }
}
