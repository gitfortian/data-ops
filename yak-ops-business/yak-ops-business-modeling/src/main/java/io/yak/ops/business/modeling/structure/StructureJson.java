package io.yak.ops.business.modeling.structure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

/** JSON (de)serialization helpers for name-lists and property maps on model rows. */
public final class StructureJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private StructureJson() {}

  public static String writeNameList(List<String> names) {
    if (names == null || names.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(names);
    } catch (Exception exception) {
      throw new IllegalStateException("serialize name list failed", exception);
    }
  }

  public static List<String> readNameList(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception exception) {
      return List.of();
    }
  }

  public static String writeProperties(Map<String, String> properties) {
    if (properties == null || properties.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(properties);
    } catch (Exception exception) {
      throw new IllegalStateException("serialize table properties failed", exception);
    }
  }

  public static Map<String, String> readProperties(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<Map<String, String>>() {});
    } catch (Exception exception) {
      return Map.of();
    }
  }
}
