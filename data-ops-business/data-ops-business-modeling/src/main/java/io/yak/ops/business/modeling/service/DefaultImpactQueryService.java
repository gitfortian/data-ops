package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;

import java.util.Collections;
import java.util.List;

/**
 * Default dispatcher for impact resolution strategies.
 */
public class DefaultImpactQueryService implements ImpactQueryService {

    private final List<ImpactResolverStrategy> strategies;

    public DefaultImpactQueryService(List<ImpactResolverStrategy> strategies) {
        this.strategies = strategies == null ? Collections.emptyList() : strategies;
    }

    @Override
    public List<ModelImpactRecord> queryImpact(String objectType, Long objectId) {
        if (objectType == null || objectId == null) {
            return Collections.emptyList();
        }
        return strategies.stream()
                .filter(strategy -> strategy.supports(objectType))
                .findFirst()
                .map(strategy -> strategy.resolve(objectType, objectId))
                .orElse(Collections.emptyList());
    }
}
