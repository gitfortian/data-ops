package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.Collections;
import java.util.List;

/**
 * Relationship adapter for logical entity impact traversal.
 */
public class LogicalEntityImpactRelationshipAdapter implements ImpactRelationshipAdapter {

    @Override
    public List<ModelImpactRecord> findImpacts(String objectType, Long objectId) {
        if (objectType == null || objectId == null) {
            return Collections.emptyList();
        }
        return Collections.emptyList();
    }
}
