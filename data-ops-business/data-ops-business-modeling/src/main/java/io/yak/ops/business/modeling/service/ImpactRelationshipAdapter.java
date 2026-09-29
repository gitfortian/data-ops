package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.List;

/**
 * Adapter abstraction for traversing model relationships.
 */
public interface ImpactRelationshipAdapter {

    List<ModelImpactRecord> findImpacts(String objectType, Long objectId);
}
