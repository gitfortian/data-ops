package io.yak.ops.business.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.MiddlewareBase;
import io.yak.ops.business.agent.config.AgentProperties;
import io.yak.ops.business.agent.toolset.AgentToolBox;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/** Records non-secret runtime settings and hashes of the actual prompt/tool contract at first reasoning. */
final class EffectiveConfigMiddleware implements MiddlewareBase {
  private final AgentObservationCollector observations;
  private final TurnCorrelation turns;
  private final Map<String, Object> settings;

  EffectiveConfigMiddleware(AgentProperties properties, List<AgentToolBox> boxes,
      AgentObservationCollector observations, TurnCorrelation turns) {
    this.observations = observations;
    this.turns = turns;
    String contracts = boxes.stream().flatMap(box -> java.util.Arrays.stream(box.getClass().getMethods()))
        .filter(method -> method.isAnnotationPresent(io.agentscope.core.tool.Tool.class))
        .map(method -> method.toGenericString() + method.getAnnotation(io.agentscope.core.tool.Tool.class).toString()
            + java.util.Arrays.deepToString(method.getParameterAnnotations()))
        .sorted().collect(java.util.stream.Collectors.joining("\n"));
    this.settings = Map.ofEntries(
        Map.entry("framework", "AgentScope Java 2.0.2"), Map.entry("contractVersion", "F-010-v1"),
        Map.entry("provider", properties.getModel().getProvider()), Map.entry("model", properties.getModel().getName()),
        Map.entry("reasoningEffort", properties.getModel().getReasoningEffort()),
        Map.entry("maxIters", properties.getChat().getMaxIters()),
        Map.entry("turnTimeoutSeconds", properties.getChat().getTurnTimeoutSeconds()),
        Map.entry("maxLlmRetries", properties.getChat().getLlmMaxRetries()),
        Map.entry("suggestionsEnabled", properties.getSuggestions().isEnabled()),
        Map.entry("toolContractHash", hash(contracts)));
  }

  @Override public Mono<String> onSystemPrompt(Agent agent, RuntimeContext context, String currentPrompt) {
    if (context.get("yak.effectiveConfig.recorded") == null) {
      context.put("yak.effectiveConfig.recorded", true);
      try {
        var snapshot = new java.util.LinkedHashMap<>(settings);
        snapshot.put("promptHash", hash(currentPrompt));
        observations.completedEvent(context.getSessionId(), turns.turnIdOf(context.getSessionId()),
            io.yak.ops.business.agent.telemetry.AgentKindRegistry.KIND_TURN_SUMMARY,
            "effective-config", null, new ObjectMapper().writeValueAsString(snapshot));
      } catch (Exception recordingFailure) {
        // Recording remains best effort; no raw prompt or configuration is logged here.
      }
    }
    return Mono.just(currentPrompt);
  }

  private static String hash(String value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
