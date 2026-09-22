package io.yak.ops.business.development.editor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.development.repository.DevelopmentEditorSettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores per-user SQL editor preferences for the data-development workbench. */
@Service
public class DevelopmentEditorSettingsService {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final DevelopmentEditorSettingRepository repository;
  private final ObjectMapper objectMapper;

  public DevelopmentEditorSettingsService(
      DevelopmentEditorSettingRepository repository,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> get(String userKey) {
    String normalizedUserKey = normalizeUserKey(userKey);
    return repository.findJson(normalizedUserKey)
        .map(this::deserialize)
        .orElseGet(this::defaults);
  }

  @Transactional
  public Map<String, Object> save(String userKey, Map<String, Object> settings) {
    Map<String, Object> normalized = defaults();
    if (settings != null) {
      normalized.putAll(settings);
    }
    normalized.put("theme", normalizeTheme(normalized.get("theme")));
    normalized.put("fontSize", clampInt(normalized.get("fontSize"), 10, 32, 14));
    normalized.put("lineHeight", clampDouble(normalized.get("lineHeight"), 1.0, 3.0, 1.6));

    try {
      repository.upsertJson(
          normalizeUserKey(userKey),
          objectMapper.writeValueAsString(normalized));
      return normalized;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to persist editor settings", ex);
    }
  }

  private Map<String, Object> deserialize(String json) {
    try {
      Map<String, Object> stored = objectMapper.readValue(json, MAP_TYPE);
      Map<String, Object> result = defaults();
      result.putAll(stored);
      return result;
    } catch (Exception ex) {
      throw new IllegalStateException("Invalid persisted editor settings", ex);
    }
  }

  private String normalizeUserKey(String userKey) {
    String value = userKey == null ? "" : userKey.trim();
    return value.isEmpty() ? "default" : value.substring(0, Math.min(value.length(), 128));
  }

  private String normalizeTheme(Object value) {
    String theme = value == null ? "Yak-Light" : String.valueOf(value).trim();
    return theme.isEmpty() ? "Yak-Light" : theme;
  }

  private int clampInt(Object value, int min, int max, int fallback) {
    try {
      int parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
      return Math.max(min, Math.min(max, parsed));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private double clampDouble(Object value, double min, double max, double fallback) {
    try {
      double parsed = value instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(value));
      return Math.max(min, Math.min(max, parsed));
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private Map<String, Object> defaults() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("theme", "Yak-Light");
    result.put("fontSize", 14);
    result.put("fontFamily", "Monaco");
    result.put("customFontFamily", "");
    result.put("lineHeight", 1.6);
    result.put("showLineNumber", true);
    result.put("showMinimap", false);
    result.put("wordWrap", true);
    result.put("folding", true);
    result.put("renderLineHighlight", "line");
    result.put("keywordCase", "lower");
    result.put("sqlCompletionFQN", "none");
    result.put("renderWhitespace", "none");
    return result;
  }
}
