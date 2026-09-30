package io.yak.ops.business.modeling.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpactGovernanceServiceTest {

    @Test
    void shouldReturnEmptyValidationResultForInvalidInput() {
        ImpactGovernanceService service = new ImpactGovernanceService() {};
        assertTrue(service.validateImpact(null, null).isEmpty());
    }
}
