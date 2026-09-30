package com.yakops.quality.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityGovernanceEventTest {

    @Test
    void shouldCreateQualityGovernanceEvent() {
        QualityGovernanceEvent event = new QualityGovernanceEvent();
        event.setDataset("customer_profile");
        event.setSeverity("HIGH");

        assertEquals("customer_profile", event.getDataset());
        assertEquals("HIGH", event.getSeverity());
    }
}
