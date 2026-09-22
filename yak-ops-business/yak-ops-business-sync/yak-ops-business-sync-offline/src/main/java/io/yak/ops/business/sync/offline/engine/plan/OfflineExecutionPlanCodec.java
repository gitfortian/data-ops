package io.yak.ops.business.sync.offline.engine.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.sync.offline.config.ConditionalOnOfflineSyncEnabled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Encodes FAN_OUT as a durable control-plane envelope while leaving direct JobSpecs unchanged. */
@ConditionalOnOfflineSyncEnabled
@Component
public class OfflineExecutionPlanCodec {

  static final String API_VERSION = "yak-ops/v1";
  static final String KIND = "OfflineExecutionPlan";

  private final ObjectMapper objectMapper;

  public OfflineExecutionPlanCodec(
      @Qualifier("offlineSyncJsonMapper") ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode encode(OfflineExecutionPlan plan) {
    if (plan == null || !plan.isFanOut()) {
      throw new IllegalArgumentException("Only FAN_OUT plans use the Yak Ops execution-plan envelope");
    }

    ObjectNode root = objectMapper.createObjectNode();
    root.put("apiVersion", API_VERSION);
    root.put("kind", KIND);
    root.put("strategy", plan.strategy().name());
    ArrayNode units = root.putArray("units");
    for (OfflineExecutionPlan.Unit unit : plan.units()) {
      ObjectNode item = units.addObject();
      item.put("unitKey", unit.unitKey());
      item.put("sourceTable", unit.sourceTable());
      item.put("sinkTable", unit.sinkTable());
      item.set("jobSpec", unit.logicalJobSpec().deepCopy());
    }
    return root;
  }

  public String encodeJson(OfflineExecutionPlan plan) {
    return write(encode(plan));
  }

  public Optional<OfflineExecutionPlan> decode(String value) {
    if (!StringUtils.hasText(value)) {
      return Optional.empty();
    }
    try {
      return decode(objectMapper.readTree(value));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Offline execution snapshot JSON is corrupted", exception);
    }
  }

  public Optional<OfflineExecutionPlan> decode(JsonNode root) {
    if (root == null || !root.isObject() || !KIND.equals(root.path("kind").asText())) {
      return Optional.empty();
    }
    if (!API_VERSION.equals(root.path("apiVersion").asText())) {
      throw new IllegalStateException(
          "Unsupported Offline execution plan apiVersion: " + root.path("apiVersion").asText());
    }

    OfflineExecutionStrategy strategy;
    try {
      strategy = OfflineExecutionStrategy.valueOf(root.path("strategy").asText());
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Unsupported Offline execution strategy: " + root.path("strategy").asText(), exception);
    }
    if (strategy != OfflineExecutionStrategy.FAN_OUT) {
      throw new IllegalStateException(
          "Yak Ops execution-plan envelope currently only persists FAN_OUT strategy");
    }

    JsonNode encodedUnits = root.path("units");
    if (!encodedUnits.isArray() || encodedUnits.isEmpty()) {
      throw new IllegalStateException("FAN_OUT execution plan contains no units");
    }

    List<OfflineExecutionPlan.Unit> units = new ArrayList<>();
    for (JsonNode item : encodedUnits) {
      String unitKey = requiredText(item, "unitKey");
      String sourceTable = requiredText(item, "sourceTable");
      String sinkTable = requiredText(item, "sinkTable");
      JsonNode jobSpec = item.get("jobSpec");
      if (jobSpec == null || !jobSpec.isObject()) {
        throw new IllegalStateException("FAN_OUT unit " + unitKey + " is missing jobSpec");
      }
      units.add(new OfflineExecutionPlan.Unit(unitKey, sourceTable, sinkTable, jobSpec));
    }
    return Optional.of(new OfflineExecutionPlan(strategy, units));
  }

  private String requiredText(JsonNode node, String field) {
    String value = node == null ? null : node.path(field).asText(null);
    if (!StringUtils.hasText(value)) {
      throw new IllegalStateException("FAN_OUT unit is missing " + field);
    }
    return value.trim();
  }

  private String write(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize Offline execution plan", exception);
    }
  }
}
