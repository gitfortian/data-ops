package io.yak.ops.business.modeling.service;

import java.util.Collections;
import java.util.List;

/**
 * PR5 impact governance service foundation.
 *
 * Provides governance entry point for impact analysis results.
 */
public interface ImpactGovernanceService {

    default List<String> validateImpact(String objectType, Long objectId) {
        return Collections.emptyList();
    }
}
