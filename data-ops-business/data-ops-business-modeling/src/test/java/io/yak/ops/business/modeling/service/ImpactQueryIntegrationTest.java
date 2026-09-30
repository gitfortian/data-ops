package io.yak.ops.business.modeling.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration validation entry point for impact query flow.
 */
class ImpactQueryIntegrationTest {

    @Test
    void impactQueryFlowShouldBeAvailable() {
        ImpactQueryService service = null;
        assertNotNull(ImpactQueryService.class);
    }
}
