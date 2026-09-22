package io.yak.ops.business.metric.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 模块统一的「名称 → 编码」自动生成策略(指标与标签共用,保证行为一致)。
 *
 * <p>规则:取名称中的字母/数字做 sanitize;名称含非 ASCII 字符(如中文)时
 * 附加稳定短哈希,避免同名或同拉丁前缀相互覆盖;纯非 ASCII 名称回退为
 * {@code {用途前缀}_{短哈希}}。哈希对同一名称确定可复现。
 */
public final class CodeGenerator {

  private static final int HASH_LENGTH = 6;
  private static final int MAX_BASE_LENGTH = 56;

  private CodeGenerator() {}

  /** 按名称生成编码;名称完全无可用字符时返回空串,由调用方决定报错。 */
  public static String fromName(String name, String fallbackPrefix) {
    if (name == null || name.isBlank()) {
      return "";
    }
    String trimmed = name.trim();
    String latin = sanitize(trimmed);
    if (latin.isEmpty()) {
      return hasNonAscii(trimmed) ? fallbackPrefix + "_" + shortHash(trimmed) : "";
    }
    return hasNonAscii(trimmed) ? truncate(latin) + "_" + shortHash(trimmed) : truncate(latin);
  }

  private static String sanitize(String name) {
    return name.toLowerCase()
        .replaceAll("[^a-z0-9]+", "_")
        .replaceAll("^_+|_+$", "");
  }

  private static boolean hasNonAscii(String name) {
    for (int i = 0; i < name.length(); i++) {
      if (name.charAt(i) > 0x7f) {
        return true;
      }
    }
    return false;
  }

  private static String truncate(String value) {
    return value.length() <= MAX_BASE_LENGTH ? value : value.substring(0, MAX_BASE_LENGTH);
  }

  private static String shortHash(String name) {
    try {
      byte[] digest = MessageDigest.getInstance("MD5").digest(name.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : digest) {
        hex.append(Character.forDigit((b >> 4) & 0xF, 16));
        hex.append(Character.forDigit(b & 0xF, 16));
      }
      return hex.substring(0, HASH_LENGTH);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("MD5 unavailable", e);
    }
  }
}
