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
  private final int maxModelInputChars;

  EffectiveConfigMiddleware(AgentProperties properties, List<AgentToolBox> boxes,
      AgentObservationCollector observations, TurnCorrelation turns) {
    this.observations = observations;
    this.turns = turns;
    this.maxModelInputChars = properties.getExecution().getMaxModelInputChars();
    String contracts = boxes.stream().flatMap(box -> java.util.Arrays.stream(box.getClass().getMethods()))
        .filter(method -> method.isAnnotationPresent(io.agentscope.core.tool.Tool.class))
        .map(method -> method.toGenericString() + method.getAnnotation(io.agentscope.core.tool.Tool.class).toString()
            + java.util.Arrays.deepToString(method.getParameterAnnotations()))
        .sorted().collect(java.util.stream.Collectors.joining("\n"));
    this.settings = Map.ofEntries(
        Map.entry("framework", "AgentScope Java 2.0.3"), Map.entry("contractVersion", "F-023-v1"),
        Map.entry("provider", properties.getModel().getProvider()), Map.entry("model", properties.getModel().getName()),
        Map.entry("reasoningEffort", properties.getModel().getReasoningEffort()),
        Map.entry("nativeStructuredOutput", properties.getModel().isNativeStructuredOutput()),
        Map.entry("nativeStructuredOutputWithTools", properties.getModel().isNativeStructuredOutputWithTools()),
        Map.entry("maxIters", properties.getChat().getMaxIters()),
        Map.entry("turnTimeoutSeconds", properties.getChat().getTurnTimeoutSeconds()),
        Map.entry("maxLlmRetries", properties.getChat().getLlmMaxRetries()),
        Map.entry("suggestionsEnabled", properties.getSuggestions().isEnabled()),
        Map.entry("toolContractHash", hash(contracts)));
  }

  @Override public reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent> onModelCall(
      Agent agent, RuntimeContext context, io.agentscope.core.middleware.ModelCallInput input,
      java.util.function.Function<io.agentscope.core.middleware.ModelCallInput,
          reactor.core.publisher.Flux<io.agentscope.core.event.AgentEvent>> next) {
    try {
      var execution = context.get(io.yak.ops.business.agent.domain.AgentExecutionContext.class);
      if (execution != null) {
        var snapshot = new java.util.LinkedHashMap<String, Object>();
        snapshot.put("taskPolicyVersion", io.yak.ops.business.agent.domain.AgentTaskToolPolicy.VERSION);
        snapshot.put("allowedTools", input.tools().stream().map(io.agentscope.core.model.ToolSchema::getName).sorted().toList());
        snapshot.put("taskToolContractHash", RuntimeContractHash.hash(new ObjectMapper().writeValueAsString(input.tools())));
        snapshot.put("maxToolCalls", execution.toolBudget().maxCalls());
        snapshot.put("maxFailuresPerTool", execution.toolBudget().maxFailuresPerTool());
        snapshot.put("budgetSource", "FROZEN_TURN");
        snapshot.put("legacyResumeBudgetInitialized", Boolean.TRUE.equals(context.get("yak.toolBudget.initializedForResume")));
        snapshot.put("maxModelInputChars", maxModelInputChars);
        snapshot.put("modelConfigSource", "STARTUP");
        snapshot.put("dynamicLlmTimeoutSource", "LLM_CALL_TRACE");
        var skillScope = context.get(ScenarioSkillScope.class);
        if (skillScope != null) {
          snapshot.put("scenarioSkillVersion", skillScope.version());
          snapshot.put("scenarioSkillHash", skillScope.hash());
        }
        String skillHash = context.get("yak.skillPromptHash");
        String loadedHash = context.get("yak.loadedSkillHash");
        snapshot.put("skillPromptHash", skillHash == null ? RuntimeContractHash.hash("") : skillHash);
        if (loadedHash != null) snapshot.put("loadedSkillHash", loadedHash);
        String json = new ObjectMapper().writeValueAsString(snapshot);
        String signature = RuntimeContractHash.hash(json);
        if (!signature.equals(context.get("yak.executionContractHash"))) {
          context.put("yak.executionContractHash", signature);
          observations.completedEvent(context.getSessionId(), turns.turnIdOf(context.getSessionId()),
              io.yak.ops.business.agent.telemetry.AgentKindRegistry.KIND_TURN_SUMMARY,
              "execution-contract", null, json);
        }
      }
    } catch (Exception recordingFailure) {
      // Best effort only; invocation guards do not depend on observation availability.
    }
    return next.apply(input);
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
