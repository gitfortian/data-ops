package io.yak.ops.business.agent.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import io.yak.ops.business.agent.domain.GovernanceTarget;
import io.yak.ops.business.asset.api.AssetGovernanceQueryApi;
import io.yak.ops.business.quality.api.QualitySuggestionQueryApi;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GovernanceSuggestionGatewayTest {
  private final QualitySuggestionQueryApi quality = mock(QualitySuggestionQueryApi.class);
  private final AssetGovernanceQueryApi assets = mock(AssetGovernanceQueryApi.class);
  private final AgentProperties properties = new AgentProperties();
  private final GovernanceSuggestionGateway gateway = new GovernanceSuggestionGateway(provider(quality), provider(assets), properties);
  private static final String HASH = "a".repeat(64);
  @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(value);
    return provider;
  }
  private AgentExecutionContext selected() {
    return new AgentExecutionContext(new GovernanceTarget(null, null, 7L, "QUALITY_RULES"));
  }
  private void context() {
    when(quality.require(7)).thenReturn(new QualitySuggestionQueryApi.Context(7, "orders", "orders", HASH,
        false, List.of(new QualitySuggestionQueryApi.Column("id", "BIGINT", null)), List.of(), false));
  }

  @Test void disabledSuggestionsAndWrongTargetsCannotReadSource() {
    properties.getSuggestions().setEnabled(false);
    assertThrows(IllegalStateException.class, () -> gateway.qualityContext(7, selected()));
    properties.getSuggestions().setEnabled(true);
    assertThrows(IllegalArgumentException.class, () -> gateway.qualityContext(8, selected()));
    assertThrows(IllegalArgumentException.class, () -> gateway.qualityRules(new AgentExecutionContext(null), "[]"));
    verifyNoInteractions(quality, assets);
  }

  @Test void validatedRulesAreServerBoundDisabledAndEvidenceBacked() {
    context();
    var state = selected();
    gateway.qualityContext(7, state);
    var candidate = new QualitySuggestionQueryApi.Candidate(1, "非空", "id", "GTE", BigDecimal.valueOf(99), null, List.of());
    when(quality.validate(eq(7L), eq(HASH), anyList())).thenReturn(List.of(candidate));
    gateway.qualityRules(state, "[{\"templateId\":1,\"name\":\"非空\",\"columnName\":\"id\",\"operator\":\"GTE\",\"threshold\":99}]");
    assertEquals(7L, state.suggestion().targetId());
    assertEquals(HASH, state.suggestion().expectedDefinition());
    assertFalse(state.suggestion().rules().getFirst().enabled());
    assertEquals(state.evidence().entries().getFirst().id(), state.suggestion().evidenceRefs().getFirst());
  }

  @Test void forgedSqlUnknownPropertiesAndExcessiveAttemptsNeverProduceAnArtifact() {
    context();
    var state = selected();
    gateway.qualityContext(7, state);
    for (int attempt = 0; attempt < 3; attempt++) {
      assertThrows(IllegalArgumentException.class, () -> gateway.qualityRules(state,
          "[{\"templateId\":1,\"customSql\":\"SELECT secret\"}]"));
    }
    assertTrue(assertThrows(IllegalArgumentException.class, () -> gateway.qualityRules(state, "[]"))
        .getMessage().contains("次数"));
    assertNull(state.suggestion());
    verify(quality, never()).validate(anyLong(), any(), any());
  }

  @Test void verifiedFactsCopySourceValuesAndRejectUnregisteredFields() {
    var state = new AgentExecutionContext(null);
    var evidence = state.evidence().register("QUALITY", "run", "OK", null, "/data-quality/execution/run");
    state.evidence().recordFacts(evidence.id(), java.util.Map.of("issueCount", "3"));
    var evidenceGateway = new GovernanceEvidenceGateway(provider(assets), provider(null));
    String result = evidenceGateway.verifyFacts(state, "[{\"evidenceRef\":\"" + evidence.id() + "\",\"field\":\"issueCount\",\"value\":999}]");
    assertTrue(result.contains("\"value\":\"3\""));
    assertFalse(result.contains("999"));
    assertThrows(IllegalArgumentException.class, () -> evidenceGateway.verifyFacts(state,
        "[{\"evidenceRef\":\"" + evidence.id() + "\",\"field\":\"password\"}]"));
  }
}
