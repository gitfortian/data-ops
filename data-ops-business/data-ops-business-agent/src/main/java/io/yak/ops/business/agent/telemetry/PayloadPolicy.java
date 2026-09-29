package io.yak.ops.business.agent.telemetry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 载荷策略引擎（设计稿 §五）：把裸文本载荷加工为 {@link PayloadEnvelope} 落库格式。
 *
 * <p>三件事：① JSON 感知截断——超限 JSON 优先收缩最长字符串叶子保结构可解析，
 * 非 JSON 或结构本身过大才退化为头尾截断；② 敏感键负清单（forbiddenRawKeys，
 * 命中即 OMITTED）；③ sha256 指纹。截断长度不再散落 magic number，统一从此处取值。</p>
 */
public final class PayloadPolicy {

  /** 默认单字段落库上限（沿用历史 8000 截断语义）。 */
  public static final int DEFAULT_MAX_INLINE_CHARS = 8000;

  /** 敏感键负清单：命中即整段 OMITTED（对齐 bkn forbiddenRawKeys 原则，I8 落地）。 */
  private static final Set<String> FORBIDDEN_RAW_KEYS =
      Set.of("raw_prompt", "rawPrompt", "raw_tool_io", "rawSql", "raw_sql", "token_data", "row_data");

  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      new com.fasterxml.jackson.databind.ObjectMapper();

  private static final String ELLIPSIS = "…[truncated]";

  private PayloadPolicy() {}

  /** INLINE 档：默认上限。 */
  public static PayloadEnvelope inline(String text) {
    return inline(text, DEFAULT_MAX_INLINE_CHARS);
  }

  /** INLINE 档：原文落库，超限做 JSON 感知截断；null 载荷返回 null（列保持空）。 */
  public static PayloadEnvelope inline(String text, int maxChars) {
    if (text == null) {
      return null;
    }
    if (containsForbiddenKey(text)) {
      return new PayloadEnvelope(
          PayloadEnvelope.MODE_OMITTED, null, null, (long) text.length(), sha256(text), null, null);
    }
    if (text.length() <= maxChars) {
      return new PayloadEnvelope(
          PayloadEnvelope.MODE_INLINE, text, false, (long) text.length(), sha256(text), null, null);
    }
    String fitted = fitJson(text, maxChars);
    boolean truncated = !fitted.equals(text);
    return new PayloadEnvelope(
        PayloadEnvelope.MODE_INLINE, fitted, truncated, (long) text.length(), sha256(text), null, null);
  }

  /**
   * SUMMARY_HASH 档：原文不落库，落结构摘要 + 末条预览 + 指纹。
   * 用于 LLM 请求内容观测（G5）——排障所需的最小可见性，隐私面最小化。
   */
  public static PayloadEnvelope summaryHash(
      String content, int messageCount, String lastUserPreview, int previewChars) {
    if (content == null) {
      return null;
    }
    String preview = clip(lastUserPreview, previewChars);
    return new PayloadEnvelope(
        PayloadEnvelope.MODE_SUMMARY_HASH,
        null,
        null,
        (long) content.length(),
        sha256(content),
        messageCount,
        preview);
  }

  /** HASH_ONLY 档：仅指纹与长度。 */
  public static PayloadEnvelope hashOnly(String content) {
    if (content == null) {
      return null;
    }
    return new PayloadEnvelope(
        PayloadEnvelope.MODE_HASH_ONLY, null, null, (long) content.length(), sha256(content), null, null);
  }

  /**
   * JSON 感知收缩：长字符串值优先批量替换（每轮替换一个去重后的值命中其全部出现），
   * 直至满足预算；结构本身过大（叶子普遍过短）才退化为头尾截断。
   */
  static String fitJson(String text, int maxChars) {
    try {
      JsonNode root = JSON.readTree(text);
      if (root.isObject() || root.isArray()) {
        java.util.LinkedHashSet<String> leafValues = new java.util.LinkedHashSet<>();
        collectLeafValues(root, leafValues);
        List<String> ordered = new ArrayList<>(leafValues);
        ordered.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String value : ordered) {
          if (jsonLength(root) <= maxChars) {
            break;
          }
          // 短值替换无净收益（省略标记本身占位），不再收缩
          if (value.length() <= ELLIPSIS.length() + 8) {
            break;
          }
          replaceValue(root, value, ELLIPSIS + "(" + value.length() + " chars)");
        }
        String fitted = JSON.writeValueAsString(root);
        if (fitted.length() <= maxChars) {
          return fitted;
        }
      }
    } catch (Exception ignored) {
      // 非 JSON：走头尾截断
    }
    return headTail(text, maxChars);
  }

  private static void collectLeafValues(JsonNode node, java.util.Set<String> out) {
    if (node instanceof ObjectNode obj) {
      obj.fields().forEachRemaining(e -> {
        JsonNode v = e.getValue();
        if (v.isTextual()) {
          out.add(v.textValue());
        } else if (v.isContainerNode()) {
          collectLeafValues(v, out);
        }
      });
    } else if (node instanceof ArrayNode arr) {
      for (JsonNode v : arr) {
        if (v.isTextual()) {
          out.add(v.textValue());
        } else if (v.isContainerNode()) {
          collectLeafValues(v, out);
        }
      }
    }
  }

  private static boolean replaceValue(JsonNode node, String from, String to) {
    if (node instanceof ObjectNode obj) {
      boolean replaced = false;
      List<String> names = new ArrayList<>();
      obj.fieldNames().forEachRemaining(names::add);
      for (String name : names) {
        JsonNode v = obj.get(name);
        if (v.isTextual() && from.equals(v.textValue())) {
          obj.set(name, TextNode.valueOf(to));
          replaced = true;
        } else if (v.isContainerNode()) {
          replaced |= replaceValue(v, from, to);
        }
      }
      return replaced;
    }
    if (node instanceof ArrayNode arr) {
      boolean replaced = false;
      for (int i = 0; i < arr.size(); i++) {
        JsonNode v = arr.get(i);
        if (v.isTextual() && from.equals(v.textValue())) {
          arr.set(i, TextNode.valueOf(to));
          replaced = true;
        } else if (v.isContainerNode()) {
          replaced |= replaceValue(v, from, to);
        }
      }
      return replaced;
    }
    return false;
  }

  private static int jsonLength(JsonNode node) {
    return node.toString().length();
  }

  private static String headTail(String text, int maxChars) {
    int half = maxChars / 2;
    return text.length() <= maxChars
        ? text
        : text.substring(0, half) + "\n...[truncated]...\n" + text.substring(text.length() - half);
  }

  private static boolean containsForbiddenKey(String text) {
    for (String key : FORBIDDEN_RAW_KEYS) {
      if (text.contains(key)) {
        return true;
      }
    }
    return false;
  }

  private static String clip(String text, int maxChars) {
    if (text == null) {
      return null;
    }
    return text.length() <= maxChars ? text : text.substring(0, maxChars) + ELLIPSIS;
  }

  static String sha256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (Exception e) {
      return null;
    }
  }
}
