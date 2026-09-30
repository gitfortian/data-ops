package io.yak.ops.business.modeling.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImpactGovernanceLifecycleTest {

    @Test
    void shouldSupportLifecycleStatus() {
        assertEquals(ImpactGovernanceResultStatus.CREATED,
                ImpactGovernanceResultStatus.valueOf("CREATED"));
    }
}
