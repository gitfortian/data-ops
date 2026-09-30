package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.Collections;
import java.util.List;

/**
 * Logical model relationship adapter.
 *
 * This adapter is the extension point for resolving model level impacts.
 */
public class LogicalModelImpactRelationshipAdapter implements ImpactRelationshipAdapter {

    @Override
    public List<ModelImpactRecord> findImpacts(String objectType, Long objectId) {
        if (objectType == null || objectId == null) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
