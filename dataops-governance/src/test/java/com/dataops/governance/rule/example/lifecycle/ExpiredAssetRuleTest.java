package com.dataops.governance.rule.example.lifecycle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExpiredAssetRuleTest {

    @Test
    void shouldCreateLifecycleRule() {
        assertNotNull(new ExpiredAssetRule());
    }
}
