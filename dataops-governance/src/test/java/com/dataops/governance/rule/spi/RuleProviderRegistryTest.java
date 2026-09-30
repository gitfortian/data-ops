package com.dataops.governance.rule.spi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.dataops.governance.rule.context.RuleContext;
import com.dataops.governance.rule.result.RuleResult;
import com.dataops.governance.rule.result.Severity;

import java.util.Collections;

class RuleProviderRegistryTest {

    @Test
    void shouldRegisterAndFindRuleProvider() {
        RuleProviderRegistry registry = new RuleProviderRegistry();
        registry.register(new GovernanceRuleProvider() {
            public String code() { return "TEST_RULE"; }
            public String name() { return "Test Rule"; }
            public RuleResult execute(RuleContext context) {
                return new RuleResult(true, Severity.INFO, Collections.emptyList());
            }
        });

        GovernanceRuleProvider provider = registry.get("TEST_RULE");
        assertNotNull(provider);
        assertEquals("TEST_RULE", provider.code());
    }
}
