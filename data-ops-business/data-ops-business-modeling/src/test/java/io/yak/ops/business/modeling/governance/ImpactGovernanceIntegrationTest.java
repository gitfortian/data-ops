package io.yak.ops.business.modeling.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpactGovernanceIntegrationTest {

    @Test
    void shouldTransitionLifecycleStatus() {
        ImpactResultLifecycleService service = new ImpactResultLifecycleService();
        assertEquals(ImpactGovernanceResultStatus.COMPLETED,
                service.transition(ImpactGovernanceResultStatus.VALIDATED,
                        ImpactGovernanceResultStatus.COMPLETED));
    }
}
