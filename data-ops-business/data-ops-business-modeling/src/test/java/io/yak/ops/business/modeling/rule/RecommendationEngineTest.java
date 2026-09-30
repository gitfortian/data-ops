package io.yak.ops.business.modeling.rule;

public class RecommendationEngineTest {

    public void shouldRecommendRuleCandidate() {
        RecommendationEngine engine = new RecommendationEngine();
        assert engine.recommend("PHONE_NULL_PATTERN")
                .contains("RULE_CANDIDATE");
    }
}
