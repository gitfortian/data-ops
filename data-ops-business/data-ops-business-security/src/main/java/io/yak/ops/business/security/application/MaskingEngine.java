package io.yak.ops.business.security.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 脱敏执行引擎:纯函数,按算法编码 + JSON 参数对单值施加脱敏。 */
public final class MaskingEngine {

  public static final String MASK_PARTIAL = "MASK_PARTIAL";
  public static final String HASH = "HASH";
  public static final String FULL_MASK = "FULL_MASK";
  public static final String NULLIFY = "NULLIFY";
  public static final String REPLACE = "REPLACE";
  public static final String KEEP_FORMAT = "KEEP_FORMAT";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private MaskingEngine() {}

  /** 支持的算法编码集合。 */
  public static java.util.Set<String> supported() {
    return java.util.Set.of(MASK_PARTIAL, HASH, FULL_MASK, NULLIFY, REPLACE, KEEP_FORMAT);
  }

  public static String mask(String value, String algoCode, String paramsJson) {
    if (value == null) {
      return null;
    }
    if (algoCode == null || !supported().contains(algoCode)) {
      throw new IllegalArgumentException("Unsupported masking algorithm: " + algoCode);
    }
    JsonNode params = readParams(paramsJson);
    validateParams(algoCode, params);
    char maskChar = charParam(params, "maskChar", '*');
    return switch (algoCode) {
      case FULL_MASK -> repeat(maskChar, Math.min(value.length(), 6));
      case NULLIFY -> "";
      case REPLACE -> repeat(charParam(params, "replacement", maskChar), value.length());
      case KEEP_FORMAT -> keepFormat(value, maskChar);
      case HASH -> hash(value, intParam(params, "length", 16));
      case MASK_PARTIAL -> maskPartial(value, maskChar,
          intParam(params, "keepLeft", 1), intParam(params, "keepRight", 1));
      default -> throw new IllegalArgumentException("Unsupported masking algorithm: " + algoCode);
    };
  }

  public static void validate(String algoCode, String paramsJson) {
    if (algoCode == null || !supported().contains(algoCode)) {
      throw new IllegalArgumentException("Unsupported masking algorithm: " + algoCode);
    }
    validateParams(algoCode, readParams(paramsJson));
  }

  private static String maskPartial(String value, char maskChar, int keepLeft, int keepRight) {
    int len = value.length();
    if (keepLeft + keepRight >= len) {
      return repeat(maskChar, len);
    }
    return value.substring(0, keepLeft)
        + repeat(maskChar, len - keepLeft - keepRight)
        + value.substring(len - keepRight);
  }

  private static String keepFormat(String value, char maskChar) {
    StringBuilder sb = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      sb.append(Character.isLetterOrDigit(c) ? maskChar : c);
    }
    return sb.toString();
  }

  private static String hash(String value, int length) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      String full = hex.toString();
      return full.length() <= length ? full : full.substring(0, length);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private static String repeat(char c, int count) {
    return String.valueOf(c).repeat(Math.max(0, count));
  }

  private static JsonNode readParams(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return MAPPER.createObjectNode();
    }
    try {
      JsonNode node = MAPPER.readTree(paramsJson);
      if (node == null || !node.isObject()) {
        throw new IllegalArgumentException("Masking parameters must be a JSON object");
      }
      return node;
    } catch (Exception exception) {
      if (exception instanceof IllegalArgumentException illegalArgumentException) {
        throw illegalArgumentException;
      }
      throw new IllegalArgumentException("Masking parameters are invalid JSON", exception);
    }
  }

  private static void validateParams(String algoCode, JsonNode params) {
    validateMaskChar(params, "maskChar");
    if (REPLACE.equals(algoCode)) {
      validateMaskChar(params, "replacement");
    }
    if (HASH.equals(algoCode)) {
      int length = intParam(params, "length", 16);
      if (length < 1 || length > 64) {
        throw new IllegalArgumentException("HASH length must be between 1 and 64");
      }
    }
    if (MASK_PARTIAL.equals(algoCode)) {
      int keepLeft = intParam(params, "keepLeft", 1);
      int keepRight = intParam(params, "keepRight", 1);
      if (keepLeft < 0 || keepRight < 0) {
        throw new IllegalArgumentException("MASK_PARTIAL keepLeft/keepRight must not be negative");
      }
    }
  }

  private static void validateMaskChar(JsonNode params, String key) {
    JsonNode node = params.get(key);
    if (node != null && (!node.isTextual() || node.asText().length() != 1)) {
      throw new IllegalArgumentException(key + " must contain exactly one character");
    }
  }

  private static int intParam(JsonNode params, String key, int defaultValue) {
    JsonNode node = params.get(key);
    return node == null || !node.isNumber() ? defaultValue : node.asInt(defaultValue);
  }

  private static char charParam(JsonNode params, String key, char defaultValue) {
    JsonNode node = params.get(key);
    if (node == null || !node.isTextual() || node.asText().isEmpty()) {
      return defaultValue;
    }
    return node.asText().charAt(0);
  }
}
