package io.yak.ops.business.modeling.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ImpactRelationshipAdapterTest {

    @Test
    void shouldReturnEmptyForInvalidInput() {
        DefaultImpactRelationshipAdapter adapter = new DefaultImpactRelationshipAdapter();
        Assertions.assertTrue(adapter.findImpacts(null, null).isEmpty());
    }
}
