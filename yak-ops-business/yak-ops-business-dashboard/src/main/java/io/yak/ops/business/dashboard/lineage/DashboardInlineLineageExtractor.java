package io.yak.ops.business.dashboard.lineage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Best-effort extractor for lineage semantics embedded in an inline Analysis payload. */
@Component
public class DashboardInlineLineageExtractor {

  private final ObjectMapper objectMapper;

  public DashboardInlineLineageExtractor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public InlineBinding extract(Object value) {
    try {
      JsonNode root = objectMapper.valueToTree(value);
      if (root == null || !root.isObject()) {
        return unresolved();
      }
      Long datasetId = positiveLong(root.get("datasetId"));
      String chartType = text(root.get("chartType"));
      JsonNode query = root.has("querySpec") && root.get("querySpec").isObject()
          ? root.get("querySpec")
          : root;

      Map<String, LinkedHashSet<String>> usages = new LinkedHashMap<>();
      addStringArray(usages, query.get("dimensions"), "DIMENSION");
      addFieldBindings(usages, query.get("metrics"), "METRIC");
      addFieldBindings(usages, query.get("filters"), "FILTER");
      addFieldBindings(usages, query.get("sorts"), "SORT");

      String status = datasetId == null
          ? "UNRESOLVED"
          : usages.isEmpty() ? "PARTIAL" : "SUCCESS";
      return new InlineBinding(datasetId, chartType, immutableUsages(usages), status);
    } catch (RuntimeException exception) {
      return unresolved();
    }
  }

  private InlineBinding unresolved() {
    return new InlineBinding(null, null, Map.of(), "UNRESOLVED");
  }

  private Map<String, List<String>> immutableUsages(
      Map<String, LinkedHashSet<String>> usages) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    usages.forEach((fieldId, roles) -> result.put(fieldId, List.copyOf(roles)));
    return Collections.unmodifiableMap(result);
  }

  private void addStringArray(
      Map<String, LinkedHashSet<String>> usages,
      JsonNode array,
      String role) {
    if (array == null || !array.isArray()) return;
    for (JsonNode item : array) {
      String fieldId = text(item);
      if (fieldId != null) usage(usages, fieldId).add(role);
    }
  }

  private void addFieldBindings(
      Map<String, LinkedHashSet<String>> usages,
      JsonNode array,
      String role) {
    if (array == null || !array.isArray()) return;
    for (JsonNode item : array) {
      if (item == null || !item.isObject()) continue;
      String fieldId = text(item.get("fieldId"));
      if (fieldId != null) usage(usages, fieldId).add(role);
    }
  }

  private Set<String> usage(Map<String, LinkedHashSet<String>> usages, String fieldId) {
    return usages.computeIfAbsent(fieldId, ignored -> new LinkedHashSet<>());
  }

  private Long positiveLong(JsonNode value) {
    if (value == null || value.isNull()) return null;
    try {
      long parsed = value.isNumber() ? value.longValue() : Long.parseLong(value.asText().trim());
      return parsed > 0L ? parsed : null;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private String text(JsonNode value) {
    if (value == null || value.isNull() || !value.isValueNode()) return null;
    String text = value.asText();
    return text == null || text.isBlank() ? null : text.trim();
  }

  public record InlineBinding(
      Long datasetId,
      String chartType,
      Map<String, List<String>> fieldUsages,
      String parseStatus) {
  }
}
