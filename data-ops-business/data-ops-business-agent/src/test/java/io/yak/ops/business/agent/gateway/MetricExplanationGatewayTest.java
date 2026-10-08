package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.yak.ops.business.agent.domain.*;
import io.yak.ops.business.metric.api.MetricExplanationQueryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricExplanationGatewayTest {
  private final MetricExplanationQueryApi api = mock(MetricExplanationQueryApi.class);
  private final MetricExplanationTarget target = new MetricExplanationTarget(7, 3, "");
  private final MetricExplanationGateway gateway = gateway();
  private MetricExplanationGateway gateway() {
    @SuppressWarnings("unchecked") var provider = (ObjectProvider<MetricExplanationQueryApi>) mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(api); return new MetricExplanationGateway(provider);
  }
  private void source(String digest) {
    when(api.require(7, 3)).thenReturn(new MetricExplanationQueryApi.Context(19, 7, 3, digest,
        List.of(new MetricExplanationQueryApi.Fact("measureExpr", "度量表达式", "SUM(amount)"))));
  }
  private MetricExplanationProposal proposal(List<String> keys) {
    return new MetricExplanationProposal(List.of(new MetricExplanationProposal.Explanation("金额合计，需核对金额含义",
        List.of(new MetricExplanationProposal.Statement("按金额求和", keys)))), List.of());
  }
  @Test void explanationEvidenceLabelsAndValuesAreEnrichedFromSource() {
    source("a"); var value = gateway.validate(target, gateway.prepare(target), proposal(List.of("measureExpr")), 1, "h");
    assertEquals("SUM(amount)", value.candidates().getFirst().statements().getFirst().evidence().getFirst().value());
    assertFalse(new AgentTaskToolPolicy(new GovernanceTarget(null, null, null, "METRIC_EXPLANATION", null, null, target)).allows("run_dataset_query"));
  }
  @Test void unknownDuplicateAndOversizeReferencesOrTextAreRejected() {
    source("a"); var original = gateway.prepare(target);
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal(List.of("owner")), 1, "h"));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal(List.of("measureExpr", "measureExpr")), 1, "h"));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original,
        new MetricExplanationProposal(List.of(), List.of("x".repeat(513))), 1, "h"));
    var choice = proposal(List.of("measureExpr")).candidates().getFirst();
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, new MetricExplanationProposal(List.of(choice, choice), List.of()), 1, "h"));
  }
  @Test void snapshotDriftRejectsEvenEmptyCompletionAndAdoption() {
    source("a"); var original = gateway.prepare(target);
    var value = gateway.validate(target, original, proposal(List.of("measureExpr")), 1, "h");
    source("changed");
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, new MetricExplanationProposal(List.of(), List.of()), 1, "h"));
    assertThrows(IllegalArgumentException.class, () -> gateway.revalidate(value));
  }
  @Test void adoptionIgnoresClientEvidenceContentAndRechecksTheSource() {
    source("a");
    var forged = new MetricExplanationSuggestion("METRIC_EXPLANATION", target, "a", 1, "h", false,
        List.of(new MetricExplanationSuggestion.Candidate("合计", List.of(new MetricExplanationSuggestion.Statement("按金额求和",
            List.of(new MetricExplanationContext.Fact("measureExpr", "伪造名称", "fake")))))), List.of());
    var value = gateway.revalidate(forged); assertEquals("SUM(amount)", value.candidates().getFirst().statements().getFirst().evidence().getFirst().value());
  }
}
