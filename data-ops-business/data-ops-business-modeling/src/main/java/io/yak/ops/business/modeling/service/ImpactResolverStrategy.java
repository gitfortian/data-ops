package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.List;

/**
 * Strategy abstraction for model impact resolution.
 *
 * Different relationship types can provide independent impact rules.
 */
public interface ImpactResolverStrategy {

    boolean supports(String objectType);

    List<ModelImpactRecord> resolve(String objectType, Long objectId);
}
