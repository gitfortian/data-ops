package io.yak.ops.business.modeling.governance;

/**
 * Handles impact governance result lifecycle transitions.
 */
public class ImpactResultLifecycleService {

    public ImpactGovernanceResultStatus transition(ImpactGovernanceResultStatus current,
                                                    ImpactGovernanceResultStatus target) {
        if (current == null || target == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        return target;
    }
}
