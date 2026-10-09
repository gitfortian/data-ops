package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.Collections;
import java.util.List;

/**
 * Persistence backed relationship adapter foundation.
 *
 * Real mapper queries can be injected here without coupling resolver logic
 * with persistence implementation details.
 */
public class PersistenceBackedImpactRelationshipAdapter implements ImpactRelationshipAdapter {

    @Override
    public List<ModelImpactRecord> findImpacts(String objectType, Long objectId) {
        return Collections.emptyList();
    }
}
