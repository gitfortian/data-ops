package com.dataops.governance.rule.example.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class SensitiveColumnRuleTest {

    @Test
    void shouldCreateSecurityRule() {
        assertNotNull(new SensitiveColumnRule());
    }
}
