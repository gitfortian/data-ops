package io.yak.ops.common.version;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 版本化对象的统一内容摘要工具（多版本契约 C2/C3 幂等发布的基础件）。
 *
 * <p>口径约定：
 * <ul>
 *   <li>{@link #canonicalJson(Object)}：Map/字段按 key 字典序序列化；null 字段以显式 null 参与摘要
 *       （即"显式 null"与"键缺失"产生不同摘要，各模块 PO 序列化时保持键齐全即可幂等）。</li>
 *   <li>{@link #sha256Fields(String...)}：逐字段 UTF-8 字节 + NUL 分隔符，与 dev-task 存量
 *       {@code TaskDefinitionDigestCalculator} 口径逐字节一致，存量 checksum 不漂移。</li>
 * </ul>
 */
public final class VersionDigests {

  private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
      .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
      .setSerializationInclusion(JsonInclude.Include.ALWAYS);

  private VersionDigests() {}

  /** 稳定序列化：同一内容任意字段序得到同一 JSON。 */
  public static String canonicalJson(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String text) {
      return text;
    }
    try {
      return CANONICAL_MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Canonical JSON serialization failed", e);
    }
  }

  /** SHA-256(UTF-8) 小写十六进制。 */
  public static String sha256Hex(String input) {
    return HexFormat.of().formatHex(newDigest().digest(input.getBytes(StandardCharsets.UTF_8)));
  }

  /** 规范化 JSON 的摘要：canonicalJson → sha256Hex。 */
  public static String sha256OfCanonical(Object value) {
    return sha256Hex(canonicalJson(value));
  }

  /** 多字段拼接摘要（NUL 分隔，null 字段仅落分隔符）；与存量任务摘要口径逐字节一致。 */
  public static String sha256Fields(String... values) {
    MessageDigest digest = newDigest();
    for (String value : values) {
      if (value != null) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
      }
      digest.update((byte) 0);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static MessageDigest newDigest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }
}
