package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.List;

/**
 * Governance oriented impact query entry point.
 */
public interface ImpactQueryService {

    List<ModelImpactRecord> queryImpact(String objectType, Long objectId);
}
