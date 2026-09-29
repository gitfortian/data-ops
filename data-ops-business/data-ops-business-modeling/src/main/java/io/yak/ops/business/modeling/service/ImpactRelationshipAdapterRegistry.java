package io.yak.ops.business.modeling.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for relationship adapters used by impact analysis.
 */
public class ImpactRelationshipAdapterRegistry {

    private final List<ImpactRelationshipAdapter> adapters = new ArrayList<>();

    public void register(ImpactRelationshipAdapter adapter) {
        if (adapter != null) {
            adapters.add(adapter);
        }
    }

    public List<ImpactRelationshipAdapter> getAdapters() {
        return adapters;
    }
}
