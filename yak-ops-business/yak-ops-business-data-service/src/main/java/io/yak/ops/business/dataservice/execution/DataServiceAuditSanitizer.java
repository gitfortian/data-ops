package io.yak.ops.business.dataservice.execution;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Masks sensitive request values before invocation evidence is serialized or persisted. */
@Component
public class DataServiceAuditSanitizer {

  static final String REDACTED = "[REDACTED]";

  public Map<String, String> sanitize(Map<String, String> parameters) {
    if (parameters == null || parameters.isEmpty()) return Map.of();
    Map<String, String> result = new LinkedHashMap<>();
    parameters.forEach((name, value) -> result.put(name, sanitizeValue(name, value)));
    return result;
  }

  String sanitizeValue(String name, String value) {
    if (value == null) return null;
    String normalized = normalize(name);
    if (isSecret(normalized)) return REDACTED;
    if (normalized.contains("mobile") || normalized.contains("phone") || normalized.contains("tel")) {
      return maskPhone(value);
    }
    if (normalized.contains("idcard") || normalized.contains("identity") || normalized.contains("身份证")) {
      return maskIdentity(value);
    }
    if (normalized.contains("email") || normalized.contains("mail")) {
      return maskEmail(value);
    }
    return value;
  }

  private boolean isSecret(String normalized) {
    return normalized.contains("password")
        || normalized.contains("passwd")
        || normalized.equals("pwd")
        || normalized.contains("secret")
        || normalized.contains("token")
        || normalized.contains("authorization")
        || normalized.contains("apikey")
        || normalized.contains("accesskey")
        || normalized.contains("credential");
  }

  private String normalize(String name) {
    if (name == null) return "";
    return name.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-.]", "");
  }

  private String maskPhone(String value) {
    String trimmed = value.trim();
    if (trimmed.length() <= 7) return REDACTED;
    return trimmed.substring(0, Math.min(3, trimmed.length()))
        + "****"
        + trimmed.substring(trimmed.length() - 4);
  }

  private String maskIdentity(String value) {
    String trimmed = value.trim();
    if (trimmed.length() <= 10) return REDACTED;
    return trimmed.substring(0, 6) + "********" + trimmed.substring(trimmed.length() - 4);
  }

  private String maskEmail(String value) {
    String trimmed = value.trim();
    int at = trimmed.indexOf('@');
    if (at <= 0 || at == trimmed.length() - 1) return REDACTED;
    String local = trimmed.substring(0, at);
    String prefix = local.isEmpty() ? "*" : local.substring(0, 1);
    return prefix + "***@" + trimmed.substring(at + 1);
  }
}
