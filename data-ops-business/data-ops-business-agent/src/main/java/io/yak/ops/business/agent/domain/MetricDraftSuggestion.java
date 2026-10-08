package io.yak.ops.business.agent.domain;

import java.util.List;

public record MetricDraftSuggestion(String kind, MetricDraftTarget target, String expectedDefinition,
    int skillVersion, String skillHash, boolean truncated, List<MetricDraftProposal.Draft> candidates,
    List<String> questions, MetricDraftContext source) {}
