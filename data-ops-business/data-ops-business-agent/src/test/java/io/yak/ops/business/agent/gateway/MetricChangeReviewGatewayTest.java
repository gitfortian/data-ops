package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.*;
import io.yak.ops.business.metric.api.MetricChangeReviewQueryApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricChangeReviewGatewayTest {
  private final MetricChangeReviewQueryApi api = mock(MetricChangeReviewQueryApi.class);
  private final MetricChangeReviewTarget target = new MetricChangeReviewTarget(7, 3, 2, 31, "a".repeat(64), "");
  private MetricChangeReviewGateway gateway() {
    @SuppressWarnings("unchecked") ObjectProvider<MetricChangeReviewQueryApi> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(api); return new MetricChangeReviewGateway(provider);
  }
  private void source(String status, String definition, long event) {
    when(api.prepare(7, 3)).thenReturn(new MetricChangeReviewQueryApi.Context(status, 7, 3, 11L, 2, event, definition, "2026-10-08",
        List.of(new MetricChangeReviewQueryApi.Difference("measureExpr", "度量", "COUNT(amount)", "SUM(amount)")),
        List.of(new MetricChangeReviewQueryApi.Fact("after.measureExpr", "原度量", "SUM(amount)")), List.of()));
  }
  private MetricChangeReviewProposal proposal(List<String> keys) {
    return new MetricChangeReviewProposal(List.of(new MetricChangeReviewProposal.Review(
        List.of(new MetricChangeReviewProposal.Statement("改为求和，业务含义仍需核对", keys)), List.of())), List.of());
  }
  @Test void sourceOwnsEvidenceAndTaskCannotQueryOrWrite() {
    source("READY", target.definition(), 31); var gateway = gateway(); var original = gateway.prepare(target);
    var value = gateway.validate(target, original, proposal(List.of("after.measureExpr")), 1, "h");
    assertEquals("SUM(amount)", value.candidates().getFirst().statements().getFirst().evidence().getFirst().value());
    var policy = new AgentTaskToolPolicy(new GovernanceTarget(null, null, null, "METRIC_CHANGE_REVIEW", null, null, null, null, target));
    assertTrue(policy.allows("get_metric_change_review_context")); assertTrue(policy.allows("load_skill_through_path"));
    assertFalse(policy.allows("run_dataset_query")); assertFalse(policy.allows("get_metric_caliber_context")); assertFalse(policy.allows("publish"));
  }
  @Test void unknownDuplicateNullKeysAndOversizeOutputAreRejected() {
    source("READY", target.definition(), 31); var gateway = gateway(); var original = gateway.prepare(target);
    for (var keys : List.of(List.of("missing"), List.of("after.measureExpr", "after.measureExpr"))) {
      assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal(keys), 1, "h"));
    }
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal(java.util.Arrays.asList((String)null)), 1, "h"));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, new MetricChangeReviewProposal(List.of(), List.of("x".repeat(513))), 1, "h"));
  }
  @Test void driftNoBaselineAndNoChangeRejectBeforeInferenceOrDelivery() {
    var gateway = gateway(); source("READY", target.definition(), 31); var original = gateway.prepare(target);
    source("READY", "b".repeat(64), 31);
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal(List.of("after.measureExpr")), 1, "h"));
    source("READY", target.definition(), 32); assertThrows(IllegalArgumentException.class, () -> gateway.prepare(target));
    for (String status : List.of("NO_BASELINE", "UNCHANGED")) {
      source(status, target.definition(), 31); assertThrows(IllegalArgumentException.class, () -> gateway.prepare(target));
    }
  }
  @Test void targetsAreExclusiveAndLegacyJsonConstructorIsStillSupported() {
    assertThrows(IllegalArgumentException.class, () -> new GovernanceTarget(1L, null, null, "METRIC_CHANGE_REVIEW", null, null, null, null, target));
    assertThrows(IllegalArgumentException.class, () -> new MetricChangeReviewTarget(7, 3, 2, 0, target.definition(), ""));
    assertNotNull(new GovernanceTarget(1L, null));
  }
  @Test void repeatedLargeEvidenceCannotProduceAnUndecodableHistoryReceipt() {
    var facts = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
        new MetricChangeReviewQueryApi.Fact("f" + i, "原事实", "x".repeat(4096))).toList();
    when(api.prepare(7, 3)).thenReturn(new MetricChangeReviewQueryApi.Context("READY", 7, 3, 11L, 2, 31L,
        target.definition(), "2026-10-08", List.of(), facts, List.of()));
    var gateway = gateway(); var original = gateway.prepare(target);
    var statement = new MetricChangeReviewProposal.Statement("请核对", List.of("f0", "f1", "f2", "f3"));
    var repeated = java.util.Collections.nCopies(5, statement);
    var proposal = new MetricChangeReviewProposal(List.of(new MetricChangeReviewProposal.Review(repeated, repeated)), List.of());
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal, 1, "h"));
  }
}
