package io.yak.ops.business.modeling.service;

import io.yak.ops.business.modeling.domain.ModelImpactRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelImpactResolverTest {

    @Test
    void shouldResolveLogicalEntityImpact() {
        ModelImpactResolver resolver = new ModelImpactResolver();

        List<ModelImpactRecord> records = resolver.resolve("LOGICAL_ENTITY", 1L);

        assertEquals(2, records.size());
        assertEquals("LOGICAL_ATTRIBUTE", records.get(0).getTargetObjectType());
    }
}
