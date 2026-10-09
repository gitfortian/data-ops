package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.Collections;
import java.util.List;

/**
 * Default relationship adapter implementation.
 *
 * Provides a stable extension point while concrete persistence relationship
 * queries are introduced incrementally.
 */
public class DefaultImpactRelationshipAdapter implements ImpactRelationshipAdapter {

    @Override
    public List<ModelImpactRecord> findImpacts(String objectType, Long objectId) {
        return Collections.emptyList();
    }
}
