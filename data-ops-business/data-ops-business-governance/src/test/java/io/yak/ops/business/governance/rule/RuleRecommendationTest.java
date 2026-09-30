package io.yak.ops.business.governance.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuleRecommendationTest {

    @Test
    void shouldCreateRuleRecommendationDomain() {
        assertNotNull(new RuleRecommendation());
    }
}
