package io.yak.ops.business.dataservice.documentation;

import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenApiRenderer {

  public Map<String, Object> render(ApiDocumentation doc) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("openapi", "3.0.3");
    root.put("info", map(
        "title", doc.name(), "version", "1.0.0", "description",
        hasText(doc.description()) ? doc.description() : "Yak Ops Data Service"));
    root.put("x-yak-documented", doc.documented());
    root.put("x-yak-schema-stale", doc.schemaStale());

    List<Map<String, Object>> parameters = new ArrayList<>();
    for (ParameterDoc parameter : doc.parameters()) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("name", parameter.name());
      item.put("in", "query");
      item.put("required", parameter.required());
      if (hasText(parameter.description())) item.put("description", parameter.description());
      item.put("schema", schema(parameter.type(), false));
      if (hasText(parameter.example())) item.put("example", parameter.example());
      parameters.add(item);
    }

    Map<String, Object> operation = new LinkedHashMap<>();
    operation.put("summary", doc.name());
    operation.put("operationId", "dataService_" + doc.apiId());
    operation.put("x-yak-schema-stale", doc.schemaStale());
    if (hasText(doc.description())) operation.put("description", doc.description());
    operation.put("parameters", parameters);
    if ("API_KEY".equals(doc.authMode())) {
      operation.put("security", List.of(Map.of("ApiKeyAuth", List.of())));
    }
    operation.put("responses", responses(doc.schemaStale() ? List.of() : doc.responseFields()));
    root.put("paths", Map.of(doc.runtimePath(), Map.of("get", operation)));
    if ("API_KEY".equals(doc.authMode())) {
      root.put("components", Map.of("securitySchemes", Map.of(
          "ApiKeyAuth", map("type", "apiKey", "in", "header", "name", "X-API-Key"))));
    }
    return root;
  }

  private Map<String, Object> responses(List<ResponseFieldDoc> fields) {
    Map<String, Object> rowProperties = new LinkedHashMap<>();
    for (ResponseFieldDoc field : fields == null ? List.<ResponseFieldDoc>of() : fields) {
      Map<String, Object> fieldSchema = schema(field.type(), field.nullable());
      if (hasText(field.description())) fieldSchema.put("description", field.description());
      if (hasText(field.example())) fieldSchema.put("example", field.example());
      rowProperties.put(field.name(), fieldSchema);
    }
    Map<String, Object> dataProperties = new LinkedHashMap<>();
    dataProperties.put("columns", map("type", "array", "items", Map.of("type", "string")));
    dataProperties.put("rows", map(
        "type", "array", "items",
        map("type", "object", "properties", rowProperties, "additionalProperties", true)));
    dataProperties.put("truncated", Map.of("type", "boolean"));
    dataProperties.put("rowCount", map("type", "integer", "format", "int32"));
    dataProperties.put("durationMs", map("type", "integer", "format", "int64"));
    Map<String, Object> envelope = map(
        "type", "object", "properties", map(
            "code", map("type", "integer", "format", "int32"),
            "data", map("type", "object", "properties", dataProperties),
            "msg", Map.of("type", "string"),
            "message", Map.of("type", "string")));
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", map(
        "description", "Success", "content",
        Map.of("application/json", Map.of("schema", envelope))));
    responses.put("401", Map.of("description", "Missing or invalid API Key"));
    responses.put("429", Map.of("description", "Rate limit exceeded"));
    responses.put("503", Map.of("description", "Runtime circuit breaker is open"));
    return responses;
  }

  private Map<String, Object> schema(String type, boolean nullable) {
    String normalized = type == null ? "STRING" : type.trim().toUpperCase();
    Map<String, Object> schema = new LinkedHashMap<>();
    switch (normalized) {
      case "INTEGER" -> {
        schema.put("type", "integer");
        schema.put("format", "int64");
      }
      case "NUMBER" -> {
        schema.put("type", "number");
        schema.put("format", "double");
      }
      case "BOOLEAN" -> schema.put("type", "boolean");
      case "DATE" -> {
        schema.put("type", "string");
        schema.put("format", "date");
      }
      case "DATETIME" -> {
        schema.put("type", "string");
        schema.put("format", "date-time");
      }
      case "OBJECT" -> {
        schema.put("type", "object");
        schema.put("additionalProperties", true);
      }
      default -> schema.put("type", "string");
    }
    if (nullable) schema.put("nullable", true);
    return schema;
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index + 1 < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }
}
