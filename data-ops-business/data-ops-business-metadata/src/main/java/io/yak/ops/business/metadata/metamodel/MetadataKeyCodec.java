package io.yak.ops.business.metadata.metamodel;

import io.yak.ops.common.enums.metadata.MetadataEnums;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 键与 FQN 的<b>唯一</b>派生处（plan §2.3 后果 4 / §3.2b 硬约束 4）。
 *
 * <p>全表只有 lineage 的一把唯一键 {@code uk (project_scope_id, asset_key)}，目录侧<b>不新增唯一键</b>
 * ——给派生值再立一把键既冗余（派生自 asset_key，锁不出新信息），又在现网存量行上根本建不起来
 * （早先那版 {@code fqn_hash NOT NULL DEFAULT '' + UNIQUE} 在 234 行副本上直接 1062）。
 * 于是"派生只有一处函数"取代了第二把键：<b>别处不得出现 {@code md5(…assetKey…)}（§10 测试 13② grep 守护）</b>。
 *
 * <p>{@code asset_key} 本身永远由源域/采集器交出，本类不替它拼键——新造一套键不会报错，只会让同一实体
 * 在血缘图里裂成两个节点（plan §2.3 后果 6）。
 */
public final class MetadataKeyCodec {

  static final int ASSET_KEY_MAX_LENGTH = 512;
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

  private MetadataKeyCodec() {}

  /** {@code md5(lower(assetKey))}，小写十六进制 32 位。 */
  public static String fqnHash(String assetKey) {
    if (assetKey == null || assetKey.isBlank()) {
      return null;
    }
    return digestHex(assetKey.toLowerCase(java.util.Locale.ROOT));
  }

  /**
   * 按 {@code type_def.fqn_pattern} 渲染展示用 FQN。
   *
   * <p>逐段处理：以 {@code key_separator} 切分模式，每段解析 {@code {占位符}}，
   * <b>解析后为空的段整段丢掉</b>（MySQL 无 schema 时 {@code {schemaName}} 为空，不该留下两个点）。
   * 字面量段原样保留。
   */
  public static String renderFqn(String fqnPattern, String keySeparator, Map<String, String> context) {
    if (fqnPattern == null || fqnPattern.isBlank()) {
      return null;
    }
    String separator = keySeparator == null || keySeparator.isBlank() ? "." : keySeparator;
    List<String> segments = new ArrayList<>();
    for (String rawSegment : splitLiteral(fqnPattern, separator)) {
      String rendered = renderSegment(rawSegment, context);
      if (!rendered.isBlank()) {
        segments.add(rendered);
      }
    }
    return String.join(separator, segments);
  }

  /** 模式里出现但上下文给不出的占位符（保存 type_def 时用它拒绝"渲染不出来的模式"）。 */
  public static List<String> unresolvedPlaceholders(String fqnPattern, Map<String, String> context) {
    List<String> missing = new ArrayList<>();
    if (fqnPattern == null || fqnPattern.isBlank()) {
      return missing;
    }
    Matcher matcher = PLACEHOLDER.matcher(fqnPattern);
    while (matcher.find()) {
      String name = matcher.group(1);
      if (context == null || context.get(name) == null || context.get(name).isBlank()) {
        missing.add(name);
      }
    }
    return missing;
  }

  /** provider/采集器交出的键：非空、≤512、前缀等于该类型登记的 key_prefix（plan §3.2b 硬约束 4）。 */
  public static KeyProblem keyProblem(String keyPrefix, String assetKey) {
    if (assetKey == null || assetKey.isBlank()) {
      return KeyProblem.BLANK;
    }
    if (assetKey.length() > ASSET_KEY_MAX_LENGTH) {
      return KeyProblem.TOO_LONG;
    }
    if (keyPrefix == null || keyPrefix.isBlank()) {
      return KeyProblem.PREFIX_NOT_DEFINED;
    }
    return assetKey.startsWith(keyPrefix) ? KeyProblem.NONE : KeyProblem.PREFIX_MISMATCH;
  }

  public enum KeyProblem {
    NONE,
    BLANK,
    TOO_LONG,
    PREFIX_NOT_DEFINED,
    PREFIX_MISMATCH
  }

  /** 槽位列名常量（与 {@link MetadataEnums.SlotName#column()} 同源，提槽写入用）。 */
  public static String slotColumn(MetadataEnums.SlotName slot) {
    return slot.column();
  }

  private static List<String> splitLiteral(String pattern, String separator) {
    List<String> parts = new ArrayList<>();
    int start = 0;
    int index;
    while ((index = pattern.indexOf(separator, start)) >= 0) {
      parts.add(pattern.substring(start, index));
      start = index + separator.length();
    }
    parts.add(pattern.substring(start));
    return parts;
  }

  private static String renderSegment(String segment, Map<String, String> context) {
    Matcher matcher = PLACEHOLDER.matcher(segment);
    StringBuilder out = new StringBuilder();
    while (matcher.find()) {
      String value = context == null ? null : context.get(matcher.group(1));
      matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : value.trim()));
    }
    matcher.appendTail(out);
    return out.toString().trim();
  }

  /**
   * 本模块<b>唯一</b>的摘要落点：MD5，小写十六进制 32 位（对齐 {@code fqn_hash}/{@code content_hash}
   * 两个 {@code CHAR(32)} 列）。结构指纹等派生一律调它，不要再各写一遍 {@code MessageDigest}。
   */
  public static String digestHex(String raw) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("MD5 unavailable", exception);
    }
  }
}
