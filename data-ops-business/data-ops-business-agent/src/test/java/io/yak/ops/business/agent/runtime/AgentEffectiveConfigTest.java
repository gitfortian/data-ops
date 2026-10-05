package io.yak.ops.business.agent.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.agentscope.core.agent.RuntimeContext;
import io.yak.ops.business.agent.config.AgentProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentEffectiveConfigTest {
  @Test void configurationEvidenceContainsHashesAndNeverSecretsOrPromptContent() {
    var properties = new AgentProperties();
    properties.getModel().setApiKey("fixture-secret");
    properties.getModel().setBaseUrl("https://private-host");
    properties.getModel().setName("fixture-model");
    var observations = mock(AgentObservationCollector.class);
    TurnCorrelation turns = session -> "turn";
    var middleware = new EffectiveConfigMiddleware(properties, List.of(), observations, turns);
    var context = RuntimeContext.builder().userId("7").sessionId("session").build();
    String prompt = "fixture prompt with private business context";
    assertEquals(prompt, middleware.onSystemPrompt(null, context, prompt).block());
    middleware.onSystemPrompt(null, context, prompt).block();
    var stats = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(observations).completedEvent(eq("session"), eq("turn"), anyString(), eq("effective-config"),
        isNull(), stats.capture());
    assertTrue(stats.getValue().contains("fixture-model"));
    assertTrue(stats.getValue().contains("promptHash"));
    assertFalse(stats.getValue().contains("fixture-secret"));
    assertFalse(stats.getValue().contains("private-host"));
    assertFalse(stats.getValue().contains("private business context"));
  }
}
