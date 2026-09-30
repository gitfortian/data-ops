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
        if (objectType == null || objectId == null) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
