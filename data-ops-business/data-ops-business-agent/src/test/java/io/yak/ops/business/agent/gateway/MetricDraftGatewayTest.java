package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.domain.*;
import io.yak.ops.business.metric.api.MetricDraftQueryApi;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class MetricDraftGatewayTest {
  private final MetricDraftQueryApi api = mock(MetricDraftQueryApi.class);
  @SuppressWarnings("unchecked") private final ObjectProvider<MetricDraftQueryApi> provider = mock(ObjectProvider.class);
  private final MetricDraftGateway gateway = new MetricDraftGateway(provider);
  private final MetricDraftTarget target = new MetricDraftTarget(null, null, "ATOMIC", 9L, List.of(), "每日金额");
  private final MetricDraftQueryApi.Context source = new MetricDraftQueryApi.Context("a".repeat(64),
      List.of(new MetricDraftQueryApi.Field("amount", "DECIMAL", "金额")), List.of());
  private final MetricDraftProposal proposal = new MetricDraftProposal(List.of(new MetricDraftProposal.Draft(
      "金额", "每日金额", "DAY", "SUM", "amount", List.of(), List.of())), List.of());
  @BeforeEach void setup() {
    when(provider.getIfAvailable()).thenReturn(api); when(api.prepare(any())).thenReturn(source);
    when(api.validate(any(), anyString(), any())).thenAnswer(i -> i.getArgument(2));
  }
  @Test void deliveryAndAdoptionBothUseSourceValidationAndIgnoreClientSourceEvidence() {
    var value = gateway.validate(target, gateway.prepare(target), proposal, 1, "hash");
    var forged = new MetricDraftSuggestion(value.kind(), target, value.expectedDefinition(), 1, "hash", false,
        value.candidates(), value.questions(), new MetricDraftContext("forged", List.of(), List.of()));
    assertEquals("amount", gateway.revalidate(forged).source().fields().getFirst().name());
    verify(api, times(2)).validate(any(), eq(source.definition()), any());
    when(api.prepare(any())).thenThrow(new SecurityException("revoked"));
    assertThrows(SecurityException.class, () -> gateway.revalidate(value));
  }
  @Test void changedDependenciesAndRejectedDefinitionsNeverBecomeDeliveries() {
    var original = gateway.prepare(target);
    when(api.prepare(any())).thenReturn(new MetricDraftQueryApi.Context("b".repeat(64), source.fields(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal, 1, "hash"));
    verify(api, never()).validate(any(), anyString(), any());
    when(api.prepare(any())).thenReturn(source);
    when(api.validate(any(), anyString(), any())).thenThrow(new IllegalArgumentException("unknown field"));
    assertThrows(IllegalArgumentException.class, () -> gateway.validate(target, original, proposal, 1, "hash"));
  }
  @Test void draftTargetAndToolScopeCannotSwitchToAnotherTaskOrExecutionTool() {
    var bound = new GovernanceTarget(null, null, null, "METRIC_DRAFT", null, null, null, target);
    var policy = new AgentTaskToolPolicy(bound);
    assertTrue(policy.allows("get_metric_draft_context")); assertTrue(policy.allows("generate_response"));
    for (String tool : List.of("run_dataset_query", "analyze_with_python", "get_metric_caliber_context", "propose_quality_rules")) {
      assertThrows(IllegalArgumentException.class, () -> policy.require(tool));
    }
    assertThrows(IllegalArgumentException.class, () -> new GovernanceTarget(7L, null, null, "METRIC_DRAFT", null, null, null, target));
    assertThrows(IllegalArgumentException.class, () -> new GovernanceTarget(null, null, null, "METRIC_EXPLANATION", null, null, null, target));
  }
}
