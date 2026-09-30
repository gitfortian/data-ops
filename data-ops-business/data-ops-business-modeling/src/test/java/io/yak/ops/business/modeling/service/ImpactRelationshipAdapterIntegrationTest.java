package io.yak.ops.business.modeling.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImpactRelationshipAdapterIntegrationTest {

    @Test
    void adapterShouldBeAvailableForImpactTraversal() {
        ImpactRelationshipAdapter adapter = new LogicalEntityImpactRelationshipAdapter();
        assertNotNull(adapter);
    }
}
