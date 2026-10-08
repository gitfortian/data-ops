package io.yak.ops.business.agent.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ToolUseBlock;
import io.yak.ops.business.agent.domain.AgentExecutionContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projects existing HITL calls; it never owns pending state or grants query permission. */
final class QueryClarificationProjection {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> KINDS = Set.of("FIELD", "TIME", "CALIBER");

  private QueryClarificationProjection() {}

  /** Per-acting-batch guard only; SDK pending remains the sole suspension owner. */
  record Batch(long clarificationCalls) {}

  static ToolUseBlock prepare(ToolUseBlock call, AgentExecutionContext execution) {
    var args = call.getInput();
    if (args == null) throw invalid();
    String question = text(args.get("question"), 2048, false);
    var options = strings(args.get("options"), 8, 512);
    var projected = new LinkedHashMap<String, Object>();
    projected.put("question", question);
    projected.put("options", options);
    Object kind = args.get("kind");
    if (kind != null) {
      if (!(kind instanceof String) || !KINDS.contains(kind)) throw invalid();
      // Governance HITL must not use this optional projection to expand its Dataset scope.
      execution.requireTool("get_dataset_fields");
      Object id = args.get("dataset_id");
      if (!(id instanceof Number number) || number.longValue() <= 0
          || number.doubleValue() != number.longValue()) throw invalid();
      var source = execution.requireDiscovery(number.longValue());
      if (source.versionNo() <= 0 || source.fields() == null) throw invalid();
      var fieldIds = strings(args.get("field_ids"), 8, 128);
      if (fieldIds.isEmpty() || ("FIELD".equals(kind) && fieldIds.size() < 2)) throw invalid();
      var fields = new ArrayList<Map<String, Object>>();
      var fieldOptions = new ArrayList<String>();
      for (String fieldId : fieldIds) {
        var matching = source.fields().stream().filter(f -> fieldId.equals(f.fieldId())).toList();
        if (matching.size() != 1) throw invalid();
        var field = matching.getFirst();
        var value = new LinkedHashMap<String, Object>();
        String name = text(field.displayName(), 256, true);
        value.put("fieldId", fieldId);
        value.put("displayName", name);
        value.put("dataType", text(field.dataType(), 64, false));
        value.put("role", text(field.role(), 32, false));
        value.put("description", text(field.description(), 512, true));
        fields.add(value);
        fieldOptions.add((name.isBlank() ? fieldId : name) + "（fieldId=" + fieldId + "）");
      }
      if ("FIELD".equals(kind)) projected.put("options", fieldOptions);
      projected.put("queryContext", Map.of("kind", kind, "datasetId", source.datasetId(),
          "versionNo", source.versionNo(), "fields", fields, "truncated", source.truncated()));
    } else if (args.get("dataset_id") != null || args.get("field_ids") != null) {
      throw invalid();
    }
    try {
      String content = JSON.writeValueAsString(projected);
      if (content.length() > 12000) throw invalid();
      // Keep the framework's original identity/input for resume pairing; content is display evidence only.
      return ToolUseBlock.builder().id(call.getId()).name(call.getName()).input(args).content(content)
          .metadata(call.getMetadata()).state(call.getState()).build();
    } catch (JsonProcessingException failed) {
      throw invalid();
    }
  }

  private static List<String> strings(Object raw, int count, int length) {
    if (raw == null) return List.of();
    if (!(raw instanceof List<?> list) || list.size() > count) throw invalid();
    var result = new ArrayList<String>();
    for (Object item : list) result.add(text(item, length, false));
    if (new HashSet<>(result).size() != result.size()) throw invalid();
    return List.copyOf(result);
  }

  private static String text(Object raw, int max, boolean optional) {
    if (raw == null && optional) return "";
    if (!(raw instanceof String value) || value.length() > max || (!optional && value.isBlank())) throw invalid();
    return value;
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("[CLARIFICATION_INVALID] 请使用本次已发现字段并提供有界、明确的澄清问题");
  }
}
